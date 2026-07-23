package com.ilink.test.ilinkcheck.adapter.inbound.wechat;

import com.github.wechat.ilink.sdk.ILinkClient;
import com.github.wechat.ilink.sdk.core.model.MessageItem;
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

@Component
@Slf4j
public class VoiceMessageProcessor {

    private final ConversationService conversationService;
    private final ReplyStrategyService replyStrategyService;
    private final WechatMessageGateway wechatMessageGateway;
    private final ImageGenerationTaskService imageGenerationTaskService;
    private final VoiceSelectionService voiceSelectionService;

    public VoiceMessageProcessor(ConversationService conversationService,
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

    public void process(ILinkClient client, String fromUserId, MessageItem item) {
        ReminderRequestContext.setCurrentUserId(fromUserId);
        try {
            var voiceItem = item.getVoice_item();
            if (voiceItem == null) {
                log.warn("语音处理器被触发，但不存在语音数据。user={}", fromUserId);
                return;
            }

            try {
                String voiceText = voiceItem.getText();
                log.info("已收到语音消息。hasTranscript={}, durationMs={}",
                        StringUtils.hasText(voiceText), voiceItem.getPlaytime());

                if (!StringUtils.hasText(voiceText)) {
                    wechatMessageGateway.sendText(client, fromUserId, "收到语音，但这条消息没有可用的转写文本。请再试一次或改发文字。");
                    return;
                }

                Optional<VoiceSelectionService.SelectionReply> selectionReply =
                        voiceSelectionService.handleInput(fromUserId, voiceText, true);
                if (selectionReply.isPresent()) {
                    VoiceSelectionService.SelectionReply reply = selectionReply.orElseThrow();
                    ReplyDraft draft = replyStrategyService.build(fromUserId, reply.text(), reply.replyMode());
                    wechatMessageGateway.sendReply(client, fromUserId, draft);
                    return;
                }
            }
            catch (Exception e) {
                log.error("语音选择指令处理失败。user={}", fromUserId, e);
                try {
                    wechatMessageGateway.sendText(client, fromUserId, "音色切换失败，请稍后再试。");
                }
                catch (Exception ignored) {
                }
                return;
            }

            wechatMessageGateway.startTyping(client, fromUserId);
            try {
                String voiceText = item.getVoice_item().getText();
                ConversationResult result = conversationService.handleVoiceTranscript(fromUserId, voiceText);
                if (result.type() == ConversationResult.Type.DRAW_IMAGE) {
                    byte[] imageBytes = imageGenerationTaskService.generate(result.content());
                    wechatMessageGateway.sendImage(client, fromUserId, imageBytes, "wanx.jpg", "图片生成成功。");
                    return;
                }

                ReplyDraft draft = replyStrategyService.build(fromUserId, result.content(), ReplyMode.AUDIO_FILE);
                wechatMessageGateway.sendReply(client, fromUserId, draft);
            }
            catch (Exception e) {
                log.error("语音消息处理失败。user={}", fromUserId, e);
                try {
                    wechatMessageGateway.sendText(client, fromUserId, "语音消息处理失败，请稍后再试。");
                }
                catch (Exception ignored) {
                }
            }
            finally {
                wechatMessageGateway.stopTyping(client, fromUserId);
            }
        }
        finally {
            ReminderRequestContext.clear();
        }
    }
}
