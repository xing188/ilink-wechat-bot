package com.ilink.test.ilinkcheck.service;

import com.ilink.test.ilinkcheck.model.ConversationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Slf4j
public class ConversationService {

    private static final String DRAW_IMAGE_PREFIX = "[DRAW_IMAGE]:";
    private static final String EMPTY_REPLY = "我暂时没有生成可用回复，请稍后再试。";
    private static final String MEMORY_INSTRUCTION = """
            你是一个微信消息路由助手。
            如果用户明确要求生成、绘制、画一张图片，你必须且只能输出格式：[DRAW_IMAGE]:英文提示词
            否则直接正常回答用户，不要附带任何额外前缀。
            """;

    private final ChatClient chatClient;
    private final String systemPrompt;

    public ConversationService(ChatClient conversationChatClient,
                               @Value("${app.chat.system-prompt:You are a concise and accurate assistant. Reply in Chinese.}") String systemPrompt) {
        this.chatClient = conversationChatClient;
        this.systemPrompt = systemPrompt;
    }

    public ConversationResult handleTextMessage(String conversationId, String text) {
        String normalizedConversationId = normalizeConversationId(conversationId);
        String normalizedText = normalizeText(text);

        String aiDecision = chatClient.prompt()
                .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, normalizedConversationId))
                .system(buildSystemPrompt())
                .user(normalizedText)
                .call()
                .content();

        if (!StringUtils.hasText(aiDecision)) {
            return ConversationResult.text(EMPTY_REPLY);
        }

        String trimmed = aiDecision.trim();
        if (trimmed.startsWith(DRAW_IMAGE_PREFIX)) {
            String prompt = trimmed.substring(DRAW_IMAGE_PREFIX.length()).trim();
            log.info("Detected image generation intent. conversationId={}, prompt={}", normalizedConversationId, prompt);
            return ConversationResult.drawImage(prompt);
        }

        return ConversationResult.text(trimmed);
    }

    public ConversationResult handleVoiceTranscript(String conversationId, String transcript) {
        return handleTextMessage(conversationId, transcript);
    }

    private String buildSystemPrompt() {
        return systemPrompt + System.lineSeparator() + MEMORY_INSTRUCTION;
    }

    private String normalizeConversationId(String conversationId) {
        if (!StringUtils.hasText(conversationId)) {
            throw new IllegalArgumentException("conversationId must not be blank");
        }
        return conversationId.trim();
    }

    private String normalizeText(String text) {
        if (!StringUtils.hasText(text)) {
            throw new IllegalArgumentException("text must not be blank");
        }
        return text.trim();
    }
}
