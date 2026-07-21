package com.ilink.test.ilinkcheck.processor;

import com.github.wechat.ilink.sdk.ILinkClient;
import com.github.wechat.ilink.sdk.core.model.MessageItem;
import com.ilink.test.ilinkcheck.model.ConversationResult;
import com.ilink.test.ilinkcheck.model.ReplyMode;
import com.ilink.test.ilinkcheck.service.ConversationService;
import com.ilink.test.ilinkcheck.service.FileParseService;
import com.ilink.test.ilinkcheck.service.ReplyService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class FileMessageProcessor {
    private final FileParseService fileParseService;;
    private final ConversationService conversationService;
    private final TypingIndicator typingIndicator;
    private final ReplyService replyService;

    public FileMessageProcessor(FileParseService fileParseService, ConversationService conversationService, TypingIndicator indicator, ReplyService replyService) {
        this.fileParseService = fileParseService;
        this.conversationService = conversationService;
        this.typingIndicator = indicator;
        this.replyService = replyService;
    }

    public void process(ILinkClient iLinkClient, String fromUserId, MessageItem item) {
        typingIndicator.start(iLinkClient, fromUserId);
        String fileName = item.getFile_item().getFile_name();

        try {
            // 1. 调用专用的 FileParseService 解析文档
            byte[] fileBytes = iLinkClient.downloadFileFromMessageItem(item);
            String fileContent = fileParseService.extractText(fileName, fileBytes);
            if (fileContent.isBlank()) {
                iLinkClient.sendText(fromUserId, "未能从文件中读取到有效文字。");
                return;
            }

            // 2. 组装提问 Prompt
            String prompt = String.format("用户发送了文档《%s》，内容如下：\n\n%s\n\n请总结该文档的核心内容：", fileName, fileContent);

            // 3. 复用现有的 ConversationService 进行 AI 思考（自动带上持久化记忆）
            ConversationResult result = conversationService.handleTextMessage(fromUserId, prompt);

            // 4. 复用现有的 ReplyService 发送回复
            replyService.sendTextOrAudioReply(iLinkClient, fromUserId, "【文件总结】\n\n" + result.content(), ReplyMode.TEXT);

        } catch (Exception e) {
            log.error("Failed to process file message. user={}, fileName={}", fromUserId, fileName, e);
            try {
                iLinkClient.sendText(fromUserId, "文件处理失败，请稍后再试。");
            } catch (Exception ignored) {
            }
        } finally {
            typingIndicator.stop(iLinkClient, fromUserId);
        }
    }
}
