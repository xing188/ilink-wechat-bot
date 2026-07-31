package com.ilink.test.ilinkcheck.adapter.inbound.wechat;

import com.github.wechat.ilink.sdk.ILinkClient;
import com.ilink.test.ilinkcheck.adapter.outbound.wechat.WechatMessageGateway;
import com.ilink.test.ilinkcheck.application.conversation.ConversationService;
import com.ilink.test.ilinkcheck.application.image.ImageGenerationTaskService;
import com.ilink.test.ilinkcheck.application.reminder.context.ReminderRequestContext;
import com.ilink.test.ilinkcheck.application.reply.ReplyDraft;
import com.ilink.test.ilinkcheck.application.reply.ReplyStrategyService;
import com.ilink.test.ilinkcheck.application.voice.VoiceSelectionService;
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
        ReminderRequestContext.setCurrentUserId(fromUserId);
        try {
            if (!StringUtils.hasText(text)) {
                return;
            }

            if (handleVoiceSelection(iLinkClient, fromUserId, text, false)) {
                return;
            }

            wechatMessageGateway.startTyping(iLinkClient, fromUserId);
            try {
                ConversationResult result = conversationService.handleTextMessage(fromUserId, text);
                ReplyMode mode = detectReplyMode(result);
                sendConversationResult(iLinkClient, fromUserId, result, mode);
            }
            catch (Exception e) {
                log.error("处理文本消息失败。user={}", fromUserId, e);
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
        finally {
            ReminderRequestContext.clear();
        }
    }

    public boolean handleVoiceSelection(ILinkClient client, String fromUserId, String text, boolean preferAudioConfirmation) {
        Optional<VoiceSelectionService.SelectionReply> selectionReply =
                voiceSelectionService.handleInput(fromUserId, text, preferAudioConfirmation);
        if (selectionReply.isPresent()) {
            VoiceSelectionService.SelectionReply reply = selectionReply.orElseThrow();
            ReplyDraft draft = replyStrategyService.build(fromUserId, reply.text(), reply.replyMode());
            wechatMessageGateway.sendReply(client, fromUserId, draft);
            return true;
        }
        return false;
    }

    public void sendConversationResult(ILinkClient client, String fromUserId, ConversationResult result, ReplyMode replyMode) {
        if (result.type() == ConversationResult.Type.DRAW_IMAGE) {
            byte[] imageBytes = imageGenerationTaskService.generate(result.content());
            wechatMessageGateway.sendImage(client, fromUserId, imageBytes, "wanx.jpg", "图片生成成功。");
            return;
        }

        ReplyDraft draft = replyStrategyService.build(fromUserId, result.content(), replyMode);
        wechatMessageGateway.sendReply(client, fromUserId, draft);
    }

    /**
     * 根据回复内容自动判断是否需要强制纯文本模式。
     * <p>结构化输出（如学习路线、审稿报告）包含明确的阶段/章节标记，
     * 用户需要在聊天窗口内直接阅读，不适合转为 Word 文档。</p>
     */
    private ReplyMode detectReplyMode(ConversationResult result) {
        if (result.type() != ConversationResult.Type.TEXT || !StringUtils.hasText(result.content())) {
            return ReplyMode.TEXT;
        }
        String content = result.content();
        // 路线/审稿类结构化输出：包含"第一阶段"、"学习路线"、"技术文档审稿报告"或修改稿标记，
        // 用户需要在聊天窗口内直接阅读，不适合转为 Word 文档
        if (content.contains("第一阶段") || content.contains("学习路线")
                || content.contains("技术文档审稿报告")
                || content.contains("已按规范帮你优化稿件")) {
            return ReplyMode.FORCE_TEXT;
        }
        return ReplyMode.TEXT;
    }
}
