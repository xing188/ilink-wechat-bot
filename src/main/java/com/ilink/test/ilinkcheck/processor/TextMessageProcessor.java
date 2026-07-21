package com.ilink.test.ilinkcheck.processor;

import com.github.wechat.ilink.sdk.ILinkClient;
import com.ilink.test.ilinkcheck.model.ConversationResult;
import com.ilink.test.ilinkcheck.model.ReplyMode;
import com.ilink.test.ilinkcheck.service.ConversationService;
import com.ilink.test.ilinkcheck.service.ImageGenerationService;
import com.ilink.test.ilinkcheck.service.ReplyService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
public class TextMessageProcessor {

    private final TypingIndicator typingIndicator;
    private final ConversationService conversationService;
    private final ReplyService replyService;
    private final ImageGenerationService imageGenerationService;

    public TextMessageProcessor(TypingIndicator typingIndicator,
                                ConversationService conversationService,
                                ReplyService replyService,
                                ImageGenerationService imageGenerationService) {
        this.typingIndicator = typingIndicator;
        this.conversationService = conversationService;
        this.replyService = replyService;
        this.imageGenerationService = imageGenerationService;
    }

    public void process(ILinkClient iLinkClient, String fromUserId, String text) {
        if (!StringUtils.hasText(text)) {
            return;
        }

        typingIndicator.start(iLinkClient, fromUserId);
        try {
            ConversationResult result = conversationService.handleTextMessage(fromUserId, text);
            if (result.type() == ConversationResult.Type.DRAW_IMAGE) {
                imageGenerationService.generateAndSend(iLinkClient, fromUserId, result.content());
                return;
            }

            replyService.sendTextOrAudioReply(iLinkClient, fromUserId, result.content(), ReplyMode.TEXT);
        }
        catch (Exception e) {
            log.error("Failed to process text message. user={}", fromUserId, e);
            try {
                iLinkClient.sendText(fromUserId, "文本消息处理失败，请稍后再试。");
            }
            catch (Exception ignored) {
            }
        }
        finally {
            typingIndicator.stop(iLinkClient, fromUserId);
        }
    }
}
