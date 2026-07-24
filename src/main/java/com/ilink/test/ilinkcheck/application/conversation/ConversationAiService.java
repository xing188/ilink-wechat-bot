package com.ilink.test.ilinkcheck.application.conversation;

import com.ilink.test.ilinkcheck.application.tool.ToolRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Slf4j
public class ConversationAiService {

    private static final String EMPTY_REPLY = "我暂时没有生成可用回复，请稍后再试。";
    private static final String MEMORY_INSTRUCTION = """
            你是一个微信消息路由助手。
            如果用户明确要求生成、绘制图片，你必须且只能严格输出格式：[DRAW_IMAGE]:英文绘图提示词。
            其余场景直接使用中文正常回答用户，不要附带任何额外前缀。
            禁止提及内部工具选择、路由调度等系统内部逻辑。
            """;
    private final ChatClient chatClient;
    private final String systemPrompt;
    private final ToolRegistry toolRegistry;

    public ConversationAiService(ChatClient conversationChatClient,
                                 @Value("${app.chat.system-prompt:You are a concise and accurate assistant. Reply in Chinese.}") String systemPrompt,
                                 ToolRegistry toolRegistry) {
        this.chatClient = conversationChatClient;
        this.systemPrompt = systemPrompt;
        this.toolRegistry = toolRegistry;
    }

    public String generateReply(String conversationId, String text) {
        String normalizedConversationId = normalizeConversationId(conversationId);
        String normalizedText = normalizeText(text);

        String aiDecision = chatClient.prompt()
                .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, normalizedConversationId))
                .tools(toolRegistry.toolObjects())
                .system(buildSystemPrompt())
                .user(normalizedText)
                .call()
                .content();

        if (!StringUtils.hasText(aiDecision)) {
            return EMPTY_REPLY;
        }

        return aiDecision.trim();
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
