package com.ilink.test.ilinkcheck.application.conversation;

import com.ilink.test.ilinkcheck.model.ConversationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Slf4j
public class ConversationResponseParser {

    private static final String DRAW_IMAGE_PREFIX = "[DRAW_IMAGE]:";
    private static final String EMPTY_REPLY = "我暂时没有生成可用回复，请稍后再试。";

    public ConversationResult parse(String conversationId, String aiReply) {
        if (!StringUtils.hasText(aiReply)) {
            return ConversationResult.text(EMPTY_REPLY);
        }

        String trimmed = aiReply.trim();
        if (trimmed.startsWith(DRAW_IMAGE_PREFIX)) {
            String prompt = trimmed.substring(DRAW_IMAGE_PREFIX.length()).trim();
            log.info("Detected image generation intent. conversationId={}, prompt={}", conversationId, prompt);
            return ConversationResult.drawImage(prompt);
        }

        return ConversationResult.text(trimmed);
    }
}
