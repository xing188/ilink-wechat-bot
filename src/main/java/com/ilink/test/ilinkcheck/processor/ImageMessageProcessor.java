package com.ilink.test.ilinkcheck.processor;

import com.github.wechat.ilink.sdk.ILinkClient;
import com.github.wechat.ilink.sdk.core.model.MessageItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.content.Media;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;
import org.springframework.util.StringUtils;

@Component
@Slf4j
public class ImageMessageProcessor {

    private static final String IMAGE_FAILURE_REPLY = "当前接入的模型没有成功识别这张图片。";

    private final ChatClient chatClient;
    private final TypingIndicator typingIndicator;

    public ImageMessageProcessor(ChatClient conversationChatClient, TypingIndicator typingIndicator) {
        this.chatClient = conversationChatClient;
        this.typingIndicator = typingIndicator;
    }

    public void process(ILinkClient ilinkClient, String fromUserId, MessageItem item) {
        typingIndicator.start(ilinkClient, fromUserId);
        try {
            log.info("Downloading image message. user={}", fromUserId);

            byte[] imageBytes = ilinkClient.downloadImageFromMessageItem(item);
            log.info("Downloaded image message. user={}, bytes={}", fromUserId, imageBytes.length);

            Media imageMedia = Media.builder()
                    .mimeType(MimeTypeUtils.IMAGE_JPEG)
                    .data(new ByteArrayResource(imageBytes))
                    .build();

            UserMessage multimodalMessage = UserMessage.builder()
                    .text("请分析这张图片的内容，并用中文简洁回答。")
                    .media(imageMedia)
                    .build();

            String aiResponse = chatClient.prompt()
                    .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, fromUserId))
                    .messages(multimodalMessage)
                    .call()
                    .content();

            if (!StringUtils.hasText(aiResponse)) {
                aiResponse = IMAGE_FAILURE_REPLY;
            }

            ilinkClient.sendTextWithTyping(fromUserId, aiResponse.trim(), 1500L);
        }
        catch (Exception e) {
            log.error("Failed to process image message. user={}", fromUserId, e);
            try {
                ilinkClient.sendText(fromUserId, IMAGE_FAILURE_REPLY);
            }
            catch (Exception ignored) {
            }
        }
        finally {
            typingIndicator.stop(ilinkClient, fromUserId);
        }
    }
}
