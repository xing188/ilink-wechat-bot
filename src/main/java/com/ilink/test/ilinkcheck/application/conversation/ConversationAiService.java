package com.ilink.test.ilinkcheck.application.conversation;

import com.ilink.test.ilinkcheck.application.tool.ToolRegistry;
import com.ilink.test.ilinkcheck.application.tool.selection.ToolContextAssembler;
import com.ilink.test.ilinkcheck.application.tool.selection.ToolSelectionResult;
import com.ilink.test.ilinkcheck.application.tool.selection.ToolSelector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Slf4j
public class ConversationAiService {

    private static final String EMPTY_REPLY = "鎴戞殏鏃舵病鏈夌敓鎴愬彲鐢ㄥ洖澶嶏紝璇风◢鍚庡啀璇曘€?";
    private static final String GLOBAL_INSTRUCTION = """
            You are a concise WeChat assistant.
            If the user explicitly asks to generate or draw an image, you must and can only output in the exact format [DRAW_IMAGE]:<English prompt>.
            Otherwise answer in Chinese.
            Do not mention hidden routing logic or internal tool selection.
            """;

    private final ChatClient chatClient;
    private final String systemPrompt;
    private final ToolRegistry toolRegistry;
    private final ToolSelector toolSelector;
    private final ToolContextAssembler toolContextAssembler;

    public ConversationAiService(ChatClient conversationChatClient,
                                 @Value("${app.chat.system-prompt:You are a concise and accurate assistant. Reply in Chinese.}") String systemPrompt,
                                 ToolRegistry toolRegistry,
                                 ToolSelector toolSelector,
                                 ToolContextAssembler toolContextAssembler) {
        this.chatClient = conversationChatClient;
        this.systemPrompt = systemPrompt;
        this.toolRegistry = toolRegistry;
        this.toolSelector = toolSelector;
        this.toolContextAssembler = toolContextAssembler;
    }

    public String generateReply(String conversationId, String text) {
        String normalizedConversationId = normalizeConversationId(conversationId);
        String normalizedText = normalizeText(text);
        ToolSelectionResult selectionResult = toolSelector.select(normalizedConversationId, normalizedText);
        Object[] selectedTools = toolRegistry.toolObjects(selectionResult.toolIds());

        log.debug("Tool selection strategy={}, tools={}", selectionResult.strategy(), selectionResult.toolIds());

        ChatClient.ChatClientRequestSpec requestSpec = chatClient.prompt()
                .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, normalizedConversationId))
                .system(buildSystemPrompt(selectionResult))
                .user(normalizedText);

        if (selectedTools.length > 0) {
            requestSpec = requestSpec.tools(selectedTools);
        }

        String aiDecision = requestSpec.call().content();
        if (!StringUtils.hasText(aiDecision)) {
            return EMPTY_REPLY;
        }

        return aiDecision.trim();
    }

    private String buildSystemPrompt(ToolSelectionResult selectionResult) {
        return systemPrompt
                + System.lineSeparator()
                + GLOBAL_INSTRUCTION
                + System.lineSeparator()
                + toolContextAssembler.buildPromptSection(selectionResult.toolIds());
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
