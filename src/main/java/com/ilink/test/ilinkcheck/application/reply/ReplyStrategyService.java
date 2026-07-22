package com.ilink.test.ilinkcheck.application.reply;

import com.ilink.test.ilinkcheck.model.ReplyMode;
import com.ilink.test.ilinkcheck.model.VoiceOption;
import com.ilink.test.ilinkcheck.infrastructure.ai.tts.TtsService;
import com.ilink.test.ilinkcheck.infrastructure.document.DocumentExportService;
import com.ilink.test.ilinkcheck.application.voice.VoicePreferenceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Slf4j
public class ReplyStrategyService {

    private static final int LONG_TEXT_THRESHOLD = 800;

    private final DocumentExportService documentExportService;
    private final TtsService ttsService;
    private final VoicePreferenceService voicePreferenceService;

    public ReplyStrategyService(TtsService ttsService,
                                DocumentExportService documentExportService,
                                VoicePreferenceService voicePreferenceService) {
        this.ttsService = ttsService;
        this.documentExportService = documentExportService;
        this.voicePreferenceService = voicePreferenceService;
    }

    public ReplyDraft build(String userId, String replyText, ReplyMode replyMode) {
        if (!StringUtils.hasText(replyText)) {
            return ReplyDraft.text("我暂时没有生成可用回复，请稍后再试。");
        }

        String normalized = replyText.trim();
        if (normalized.length() > LONG_TEXT_THRESHOLD) {
            log.info("Reply length={} exceeds threshold={}, preparing docx draft.",
                    normalized.length(), LONG_TEXT_THRESHOLD);

            byte[] docxBytes = documentExportService.exportToDocx("AI详细回复", normalized);
            return ReplyDraft.file(
                    docxBytes,
                    "AI详细回复.docx",
                    "回复内容较长，已为你整理成 Word 文档，请查收。",
                    "回复内容较长，已为你整理成 Word 文档，请查收。");
        }

        if (replyMode == ReplyMode.AUDIO_FILE) {
            try {
                VoiceOption voiceOption = voicePreferenceService.getUserVoiceOption(userId);
                byte[] audioBytes = ttsService.synthesizeMp3(normalized, voiceOption.getVoiceParam());
                return ReplyDraft.file(audioBytes, "reply.mp3", normalized, normalized);
            }
            catch (Exception e) {
                log.warn("Failed to prepare audio reply. user={}", userId, e);
            }
        }

        return ReplyDraft.text(normalized);
    }
}
