package com.ilink.test.ilinkcheck.service;

import com.github.wechat.ilink.sdk.ILinkClient;
import com.ilink.test.ilinkcheck.model.ReplyMode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Slf4j
public class ReplyService {

    private static final int LONG_TEXT_THRESHOLD = 800; //超过800字转文档
    private final DocumentExportService documentExportService;
    private final TtsService ttsService;

    public ReplyService(TtsService ttsService, DocumentExportService documentExportService) {
        this.ttsService = ttsService;
        this.documentExportService = documentExportService;
    }

    public void sendTextOrAudioReply(ILinkClient iLinkClient, String fromUserId, String replyText, ReplyMode replyMode) throws Exception {
        if (!StringUtils.hasText(replyText)) {
            iLinkClient.sendText(fromUserId, "我暂时没有生成可用回复，请稍后再试。");
            return;
        }

        String normalized = replyText.trim();
        try {
            // 💡 1. 判断是否触发超长文本逻辑
            if (normalized.length() > LONG_TEXT_THRESHOLD) {
                log.info("回复内容长度为 {} 字，超过阈值 {}，自动转换为 Word 文档发送", normalized.length(), LONG_TEXT_THRESHOLD);

                // 生成 Word 文档字节流
                byte[] docxBytes = documentExportService.exportToDocx("AI详细回复", normalized);
                String fileName = "AI详细回复.docx";

                // 先发一条简短提示，再发文档文件
                String caption = "回复内容较长，已为你整理为 Word 文档，请查收！";
                iLinkClient.sendFile(fromUserId, docxBytes, fileName, caption);
                return;
            }
            if (replyMode == ReplyMode.AUDIO_FILE) {
                try {
                    byte[] audioBytes = ttsService.synthesizeMp3(normalized);
                    iLinkClient.sendFile(fromUserId, audioBytes, "reply.mp3", normalized);
                    return;
                }
                catch (Exception e) {
                    log.warn("Failed to synthesize or send mp3 reply. Falling back to text. user={}", fromUserId, e);
                }
            }
            iLinkClient.sendTextWithTyping(fromUserId, normalized, 1200L);
        } catch (Exception e) {
            log.error("发送回复失败, user={}", fromUserId, e);
            // 兜底：如果文件发送失败，降级为正常文本发送
            try {
                iLinkClient.sendText(fromUserId, normalized);
            } catch (Exception ignored) {}
        }
    }
}
