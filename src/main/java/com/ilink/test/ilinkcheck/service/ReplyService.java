package com.ilink.test.ilinkcheck.service;

import com.github.wechat.ilink.sdk.ILinkClient;
import com.ilink.test.ilinkcheck.model.ReplyMode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Slf4j
public class ReplyService {

    private static final int LONG_TEXT_THRESHOLD = 800;

    private final DocumentExportService documentExportService;
    private final TtsService ttsService;
    private final VoicePreferenceService voicePreferenceService;

    public ReplyService(TtsService ttsService,
                        DocumentExportService documentExportService,
                        VoicePreferenceService voicePreferenceService) {
        this.ttsService = ttsService;
        this.documentExportService = documentExportService;
        this.voicePreferenceService = voicePreferenceService;
    }

    public void sendTextOrAudioReply(ILinkClient iLinkClient, String fromUserId, String replyText, ReplyMode replyMode)
            throws Exception {
        if (!StringUtils.hasText(replyText)) {
            iLinkClient.sendText(fromUserId, "我暂时没有生成可用回复，请稍后再试。");
            return;
        }

        String normalized = replyText.trim();
        try {
            if (normalized.length() > LONG_TEXT_THRESHOLD) {
                log.info("Reply length={} exceeds threshold={}, sending docx instead.",
                        normalized.length(), LONG_TEXT_THRESHOLD);

                byte[] docxBytes = documentExportService.exportToDocx("AI详细回复", normalized);
                iLinkClient.sendFile(fromUserId, docxBytes, "AI详细回复.docx",
                        "回复内容较长，已为你整理成 Word 文档，请查收。");
                return;
            }

            if (replyMode == ReplyMode.AUDIO_FILE) {
                try {
                    String voiceParam = voicePreferenceService.getUserVoiceParam(fromUserId);
                    byte[] audioBytes = ttsService.synthesizeMp3(normalized, voiceParam);
                    iLinkClient.sendFile(fromUserId, audioBytes, "reply.mp3", normalized);
                    return;
                }
                catch (Exception e) {
                    log.warn("Failed to synthesize or send mp3 reply. user={}", fromUserId, e);
                }
            }

            iLinkClient.sendTextWithTyping(fromUserId, normalized, 1200L);
        }
        catch (Exception e) {
            log.error("Failed to send reply. user={}", fromUserId, e);
            try {
                iLinkClient.sendText(fromUserId, normalized);
            }
            catch (Exception ignored) {
            }
        }
    }
}
