package com.ilink.test.ilinkcheck.processor;

import com.github.wechat.ilink.sdk.ILinkClient;
import com.github.wechat.ilink.sdk.core.model.MessageItem;
import com.ilink.test.ilinkcheck.model.ConversationResult;
import com.ilink.test.ilinkcheck.model.ReplyMode;
import com.ilink.test.ilinkcheck.service.ConversationService;
import com.ilink.test.ilinkcheck.service.ImageGenerationService;
import com.ilink.test.ilinkcheck.service.ReplyService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Slf4j
public class VoiceMessageProcessor {

    private final TypingIndicator typingIndicator;
    private final ConversationService conversationService;
    private final ReplyService replyService;
    private final ImageGenerationService imageGenerationService;

    public VoiceMessageProcessor(TypingIndicator typingIndicator,
                                 ConversationService conversationService,
                                 ReplyService replyService,
                                 ImageGenerationService imageGenerationService) {
        this.typingIndicator = typingIndicator;
        this.conversationService = conversationService;
        this.replyService = replyService;
        this.imageGenerationService = imageGenerationService;
    }

    public void process(ILinkClient client, String fromUserId, MessageItem item) {
        var voiceItem = item.getVoice_item();
        if (voiceItem == null) {
            log.warn("Voice processor triggered without voice payload. user={}", fromUserId);
            return;
        }

        typingIndicator.start(client, fromUserId);
        try {
            String voiceText = voiceItem.getText();
            log.info("Received voice message. hasTranscript={}, durationMs={}",
                    StringUtils.hasText(voiceText), voiceItem.getPlaytime());

            if (!StringUtils.hasText(voiceText)) {
                client.sendText(fromUserId, "收到了语音，但这条消息没有可用的转写文本。请再试一次或改发文字。");
                return;
            }

            ConversationResult result = conversationService.handleVoiceTranscript(fromUserId, voiceText);
            if (result.type() == ConversationResult.Type.DRAW_IMAGE) {
                imageGenerationService.generateAndSend(client, fromUserId, result.content());
                return;
            }

            replyService.sendTextOrAudioReply(client, fromUserId, result.content(), ReplyMode.AUDIO_FILE);
        }
        catch (Exception e) {
            log.error("Failed to process voice message. user={}", fromUserId, e);
            try {
                client.sendText(fromUserId, "语音消息处理失败，请稍后再试。");
            }
            catch (Exception ignored) {
            }
        }
        finally {
            typingIndicator.stop(client, fromUserId);
        }
    }
}
