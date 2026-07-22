package com.ilink.test.ilinkcheck.adapter.inbound.wechat;

import com.github.wechat.ilink.sdk.ILinkClient;
import com.ilink.test.ilinkcheck.adapter.outbound.wechat.WechatMessageGateway;
import com.ilink.test.ilinkcheck.application.image.ImageGenerationTaskService;
import com.ilink.test.ilinkcheck.application.voice.VoiceSelectionService;
import com.ilink.test.ilinkcheck.application.reply.ReplyDraft;
import com.ilink.test.ilinkcheck.application.reply.ReplyStrategyService;
import com.ilink.test.ilinkcheck.application.conversation.ConversationService;
import com.ilink.test.ilinkcheck.model.ConversationResult;
import com.ilink.test.ilinkcheck.model.ReplyMode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Optional;

@Slf4j
@Component
public class TextMessageProcessor {

    private final ConversationService conversationService;
    private final ReplyStrategyService replyStrategyService;
    private final WechatMessageGateway wechatMessageGateway;
    private final ImageGenerationTaskService imageGenerationTaskService;
    private final VoiceSelectionService voiceSelectionService;

    public TextMessageProcessor(ConversationService conversationService,
                                ReplyStrategyService replyStrategyService,
                                WechatMessageGateway wechatMessageGateway,
                                ImageGenerationTaskService imageGenerationTaskService,
                                VoiceSelectionService voiceSelectionService) {
        this.conversationService = conversationService;
        this.replyStrategyService = replyStrategyService;
        this.wechatMessageGateway = wechatMessageGateway;
        this.imageGenerationTaskService = imageGenerationTaskService;
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
            ReplyDraft draft = replyStrategyService.build(fromUserId, reply.text(), reply.replyMode());
            wechatMessageGateway.sendReply(iLinkClient, fromUserId, draft);
            return;
        }

        wechatMessageGateway.startTyping(iLinkClient, fromUserId);
        try {
            ConversationResult result = conversationService.handleTextMessage(fromUserId, text);
            if (result.type() == ConversationResult.Type.DRAW_IMAGE) {
                byte[] imageBytes = imageGenerationTaskService.generate(result.content());
                wechatMessageGateway.sendImage(iLinkClient, fromUserId, imageBytes, "wanx.jpg", "图片生成成功。");
                return;
            }

            ReplyDraft draft = replyStrategyService.build(fromUserId, result.content(), ReplyMode.TEXT);
            wechatMessageGateway.sendReply(iLinkClient, fromUserId, draft);
        }
        catch (Exception e) {
            log.error("处理文本消息失败。 user={}", fromUserId, e);
            try {
                wechatMessageGateway.sendText(iLinkClient, fromUserId, "文本消息处理失败，请稍后再试。");
            }
            catch (Exception ignored) {
            }
        }
        finally {
            wechatMessageGateway.stopTyping(iLinkClient, fromUserId);
        }
    }
}
