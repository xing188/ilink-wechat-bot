package com.ilink.test.ilinkcheck.processor;

import com.github.wechat.ilink.sdk.ILinkClient;
import com.github.wechat.ilink.sdk.core.model.MessageItem;
import com.github.wechat.ilink.sdk.core.model.WeixinMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class MessageDispatcher {

    private final TextMessageProcessor textMessageProcessor;
    private final ImageMessageProcessor imageMessageProcessor;
    private final VoiceMessageProcessor voiceMessageProcessor;
    private final FileMessageProcessor fileMessageProcessor;

    public MessageDispatcher(TextMessageProcessor textMessageProcessor,
                             ImageMessageProcessor imageMessageProcessor,
                             VoiceMessageProcessor voiceMessageProcessor, FileMessageProcessor fileMessageProcessor) {
        this.textMessageProcessor = textMessageProcessor;
        this.imageMessageProcessor = imageMessageProcessor;
        this.voiceMessageProcessor = voiceMessageProcessor;
        this.fileMessageProcessor = fileMessageProcessor;
    }

    public void dispatch(ILinkClient client, WeixinMessage message) {
        if (message.getItem_list() == null || message.getItem_list().isEmpty()) {
            return;
        }

        String fromUserId = message.getFrom_user_id();
        for (MessageItem item : message.getItem_list()) {
            if (item.getVoice_item() != null) {
                log.info("收到来自 user {} 的语音消息", fromUserId);
                voiceMessageProcessor.process(client, fromUserId, item);
            }
            else if (item.getImage_item() != null) {
                log.info("收到来自 user {} 的图片消息", fromUserId);
                imageMessageProcessor.process(client, fromUserId, item);
            }
            else if (item.getText_item() != null) {
                String text = item.getText_item().getText();
                log.info("收到来自 user {} 的文本消息, textBlank={}", fromUserId, text == null || text.isBlank());
                textMessageProcessor.process(client, fromUserId, text);
            }else if(item.getFile_item() != null) {
                log.info("收到来自 user {} 的文件消息", fromUserId);
                fileMessageProcessor.process(client, fromUserId, item);
            }
            else {
                log.info("暂不支持来自 user {} 的其他类型消息, type={}", fromUserId, item.getType());
            }
        }
    }
}
