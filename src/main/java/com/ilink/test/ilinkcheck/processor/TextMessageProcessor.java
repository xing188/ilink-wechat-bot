package com.ilink.test.ilinkcheck.processor;

import com.github.wechat.ilink.sdk.ILinkClient;
import com.ilink.test.ilinkcheck.model.ConversationResult;
import com.ilink.test.ilinkcheck.model.ReplyMode;
import com.ilink.test.ilinkcheck.service.ConversationService;
import com.ilink.test.ilinkcheck.service.ImageGenerationService;
import com.ilink.test.ilinkcheck.service.ReplyService;
import com.ilink.test.ilinkcheck.service.VoiceSelectionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Optional;

@Slf4j
@Component
public class TextMessageProcessor {

    private final TypingIndicator typingIndicator;
    private final ConversationService conversationService;
    private final ReplyService replyService;
    private final ImageGenerationService imageGenerationService;
    private final VoiceSelectionService voiceSelectionService;

    public TextMessageProcessor(TypingIndicator typingIndicator,
                                ConversationService conversationService,
                                ReplyService replyService,
                                ImageGenerationService imageGenerationService,
                                VoiceSelectionService voiceSelectionService) {
        this.typingIndicator = typingIndicator;
        this.conversationService = conversationService;
        this.replyService = replyService;
        this.imageGenerationService = imageGenerationService;
        this.voiceSelectionService = voiceSelectionService;
    }

    public void process(ILinkClient iLinkClient, String fromUserId, String text) {
        if (!StringUtils.hasText(text)) {
            return;
        }

        Optional<VoiceSelectionService.SelectionReply> selectionReply =
                voiceSelectionService.handleInput(fromUserId, text, false);
        if (selectionReply.isPresent()) {
            VoiceSelectionService.SelectionReply reply = selectionReply.orElseThrow();
            try {
                replyService.sendTextOrAudioReply(
                        iLinkClient,
                        fromUserId,
                        reply.text(),
                        reply.replyMode());
            }
            catch (Exception e) {
                log.error("Failed to handle voice selection text command. user={}", fromUserId, e);
                try {
                    iLinkClient.sendText(fromUserId, "音色切换失败，请稍后再试。");
                }
                catch (Exception ignored) {
                }
            }
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
