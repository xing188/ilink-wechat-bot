package com.ilink.test.ilinkcheck.adapter.outbound.wechat;

import com.github.wechat.ilink.sdk.ILinkClient;
import com.github.wechat.ilink.sdk.core.model.MessageItem;
import com.ilink.test.ilinkcheck.application.reply.ReplyDraft;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class WechatMessageGateway {

    public void startTyping(ILinkClient client, String userId) {
        try {
            client.startTyping(userId);
        }
        catch (Exception e) {
            log.warn("Failed to start typing for user {}: {}", userId, e.getMessage());
        }
    }

    public void stopTyping(ILinkClient client, String userId) {
        try {
            client.stopTyping(userId);
        }
        catch (Exception e) {
            log.warn("Failed to stop typing for user {}: {}", userId, e.getMessage());
        }
    }

    public void sendText(ILinkClient client, String userId, String text) {
        try {
            client.sendText(userId, text);
        }
        catch (Exception e) {
            log.error("Failed to send text. user={}", userId, e);
        }
    }

    public void sendTextWithTyping(ILinkClient client, String userId, String text, long delayMs) {
        try {
            client.sendTextWithTyping(userId, text, delayMs);
        }
        catch (Exception e) {
            log.error("Failed to send text with typing. user={}", userId, e);
            sendText(client, userId, text);
        }
    }

    public void sendReply(ILinkClient client, String userId, ReplyDraft draft) {
        try {
            if (draft.type() == ReplyDraft.Type.TEXT) {
                sendTextWithTyping(client, userId, draft.text(), 1200L);
                return;
            }

            client.sendFile(userId, draft.fileBytes(), draft.fileName(), draft.caption());
        }
        catch (Exception e) {
            log.error("Failed to send reply. user={}", userId, e);
            try {
                sendText(client, userId, draft.fallbackText());
            }
            catch (Exception ignored) {
            }
        }
    }

    public void sendImage(ILinkClient client, String userId, byte[] imageBytes, String fileName, String caption) {
        try {
            client.sendImage(userId, imageBytes, fileName, caption);
        }
        catch (Exception e) {
            log.error("Failed to send image. user={}", userId, e);
            try {
                sendText(client, userId, "图片发送失败，请稍后再试。");
            }
            catch (Exception ignored) {
            }
        }
    }

    public byte[] downloadImage(ILinkClient client, MessageItem item) {
        try {
            return client.downloadImageFromMessageItem(item);
        }
        catch (Exception e) {
            throw new RuntimeException("Failed to download image message", e);
        }
    }

    public byte[] downloadFile(ILinkClient client, MessageItem item) {
        try {
            return client.downloadFileFromMessageItem(item);
        }
        catch (Exception e) {
            throw new RuntimeException("Failed to download file message", e);
        }
    }
}
