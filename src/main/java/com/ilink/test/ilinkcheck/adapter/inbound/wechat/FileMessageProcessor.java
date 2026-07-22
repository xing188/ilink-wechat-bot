package com.ilink.test.ilinkcheck.adapter.inbound.wechat;

import com.github.wechat.ilink.sdk.ILinkClient;
import com.github.wechat.ilink.sdk.core.model.MessageItem;
import com.ilink.test.ilinkcheck.adapter.outbound.wechat.WechatMessageGateway;
import com.ilink.test.ilinkcheck.application.reply.ReplyDraft;
import com.ilink.test.ilinkcheck.application.reply.ReplyStrategyService;
import com.ilink.test.ilinkcheck.application.conversation.ConversationService;
import com.ilink.test.ilinkcheck.infrastructure.document.FileParseService;
import com.ilink.test.ilinkcheck.model.ConversationResult;
import com.ilink.test.ilinkcheck.model.ReplyMode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class FileMessageProcessor {

    private final FileParseService fileParseService;
    private final ConversationService conversationService;
    private final ReplyStrategyService replyStrategyService;
    private final WechatMessageGateway wechatMessageGateway;

    public FileMessageProcessor(FileParseService fileParseService,
                                ConversationService conversationService,
                                ReplyStrategyService replyStrategyService,
                                WechatMessageGateway wechatMessageGateway) {
        this.fileParseService = fileParseService;
        this.conversationService = conversationService;
        this.replyStrategyService = replyStrategyService;
        this.wechatMessageGateway = wechatMessageGateway;
    }

    public void process(ILinkClient iLinkClient, String fromUserId, MessageItem item) {
        wechatMessageGateway.startTyping(iLinkClient, fromUserId);
        String fileName = item.getFile_item().getFile_name();

        try {
            byte[] fileBytes = wechatMessageGateway.downloadFile(iLinkClient, item);
            String fileContent = fileParseService.extractText(fileName, fileBytes);
            if (fileContent.isBlank()) {
                wechatMessageGateway.sendText(iLinkClient, fromUserId, "未能从文件中读取到有效文字。");
                return;
            }

            String prompt = String.format("用户发送了文档《%s》，内容如下：\n\n%s\n\n请总结该文档的核心内容：", fileName, fileContent);
            ConversationResult result = conversationService.handleTextMessage(fromUserId, prompt);

            ReplyDraft draft = replyStrategyService.build(fromUserId, "【文件总结】\n\n" + result.content(), ReplyMode.TEXT);
            wechatMessageGateway.sendReply(iLinkClient, fromUserId, draft);
        }
        catch (Exception e) {
            log.error("处理文件失败. user={}, fileName={}", fromUserId, fileName, e);
            try {
                wechatMessageGateway.sendText(iLinkClient, fromUserId, "文件处理失败，请稍后再试。");
            }
            catch (Exception ignored) {
            }
        }
        finally {
            wechatMessageGateway.stopTyping(iLinkClient, fromUserId);
        }
    }
}
