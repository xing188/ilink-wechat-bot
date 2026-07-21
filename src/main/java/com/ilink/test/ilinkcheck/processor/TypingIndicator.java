package com.ilink.test.ilinkcheck.processor;

import com.github.wechat.ilink.sdk.ILinkClient;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class TypingIndicator {

    public void start(ILinkClient client, String toUserId) {
        try {
            client.startTyping(toUserId);
        }
        catch (Exception e) {
            log.warn("Failed to start typing for user {}: {}", toUserId, e.getMessage());
        }
    }

    public void stop(ILinkClient client, String toUserId) {
        try {
            client.stopTyping(toUserId);
        }
        catch (Exception e) {
            log.warn("Failed to stop typing for user {}: {}", toUserId, e.getMessage());
        }
    }
}
