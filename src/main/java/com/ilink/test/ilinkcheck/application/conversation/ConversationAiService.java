package com.ilink.test.ilinkcheck.application.conversation;

import com.ilink.test.ilinkcheck.application.tool.ToolRegistry;
import com.ilink.test.ilinkcheck.application.tool.orchestration.model.ToolOrchestrationResult;
import com.ilink.test.ilinkcheck.application.tool.orchestration.ToolOrchestrator;
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

    private static final String EMPTY_REPLY = "我暂时没有生成可用回复，请稍后再试。";
    private static final String MEMORY_INSTRUCTION = """
            你是一个微信消息助理。
            如果用户明确要求生成或绘制图片，你必须且只能严格输出格式：[DRAW_IMAGE]:英文绘图提示词。
            其余场景直接用中文正常回答用户，不要附带任何额外前缀。
            不要提及内部工具选择、路由调度等系统内部逻辑。
            """;

    private final ChatClient chatClient;
    private final String systemPrompt;
    private final ToolRegistry toolRegistry;
    private final ToolSelector toolSelector;
    private final ToolContextAssembler toolContextAssembler;
    private final ToolOrchestrator toolOrchestrator;

    public ConversationAiService(ChatClient conversationChatClient,
                                 @Value("${app.chat.system-prompt:You are a concise and accurate assistant. Reply in Chinese.}") String systemPrompt,
                                 ToolRegistry toolRegistry,
                                 ToolSelector toolSelector,
                                 ToolContextAssembler toolContextAssembler,
                                 ToolOrchestrator toolOrchestrator) {
        this.chatClient = conversationChatClient;
        this.systemPrompt = systemPrompt;
        this.toolRegistry = toolRegistry;
        this.toolSelector = toolSelector;
        this.toolContextAssembler = toolContextAssembler;
        this.toolOrchestrator = toolOrchestrator;
    }

    public String generateReply(String conversationId, String text) {
        String normalizedConversationId = normalizeConversationId(conversationId);
        String normalizedText = normalizeText(text);

        ToolSelectionResult selection = toolSelector.select(normalizedConversationId, normalizedText);
        Object[] selectedToolObjects = toolRegistry.toolObjects(selection.toolIds());
        String toolPromptSection = toolContextAssembler.buildPromptSection(selection.toolIds());

        log.info("conversationId={}, toolSelectionStrategy={}, selectedTools={}",
                normalizedConversationId, selection.strategy(), selection.toolIds());

        ToolOrchestrationResult orchestrationResult = toolOrchestrator.orchestrate(
                normalizedConversationId,
                normalizedText,
                selection.toolIds(),
                buildSystemPrompt(toolPromptSection)
        );
        if (orchestrationResult != null && StringUtils.hasText(orchestrationResult.finalReply())) {
            log.info("conversationId={}, plannerNote={}, parallelUsed={}, executedSteps={}",
                    normalizedConversationId,
                    orchestrationResult.plan().plannerNote(),
                    orchestrationResult.usedParallelExecution(),
                    orchestrationResult.outcomes().stream().map(outcome -> outcome.step().toolName()).toList());
            return orchestrationResult.finalReply();
        }

        log.info("conversationId={}, orchestrationFallback=direct-tool-calling, selectedTools={}",
                normalizedConversationId, selection.toolIds());

        ChatClient.ChatClientRequestSpec requestSpec = chatClient.prompt()
                .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, normalizedConversationId));

        if (selectedToolObjects.length > 0) {
            requestSpec = requestSpec.tools(selectedToolObjects);
        }

        String aiDecision = requestSpec
                .system(buildSystemPrompt(toolPromptSection))
                .user(normalizedText)
                .call()
                .content();

        if (!StringUtils.hasText(aiDecision)) {
            return EMPTY_REPLY;
        }

        return aiDecision.trim();
    }

    private String buildSystemPrompt(String toolPromptSection) {
        return String.join(System.lineSeparator(), systemPrompt, MEMORY_INSTRUCTION, toolPromptSection);
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
