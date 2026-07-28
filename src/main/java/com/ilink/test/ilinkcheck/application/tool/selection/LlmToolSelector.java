package com.ilink.test.ilinkcheck.application.tool.selection;

import com.ilink.test.ilinkcheck.application.tool.ToolMetadata;
import com.ilink.test.ilinkcheck.application.tool.ToolRegistry;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

@Service
public class LlmToolSelector {

    private static final int MAX_SELECTED_TOOLS = 3;

    private final ChatClient chatClient;
    private final ToolRegistry toolRegistry;

    public LlmToolSelector(@Qualifier("routingChatClient") ChatClient routingChatClient,
                           ToolRegistry toolRegistry) {
        this.chatClient = routingChatClient;
        this.toolRegistry = toolRegistry;
    }

    public Optional<ToolSelectionResult> trySelect(String userMessage) {
        if (!StringUtils.hasText(userMessage)) {
            return Optional.empty();
        }

        String response = chatClient.prompt()
                .system(buildSystemPrompt())
                .user("Latest user message:\n" + userMessage)
                .call()
                .content();

        if (!StringUtils.hasText(response)) {
            return Optional.empty();
        }

        String normalized = response.trim();
        if ("NONE".equalsIgnoreCase(normalized)) {
            return Optional.of(ToolSelectionResult.noTools("llm-none"));
        }

        LinkedHashSet<String> selected = new LinkedHashSet<>();
        for (String token : normalized.split("[,，\\s]+")) {
            if (!StringUtils.hasText(token)) {
                continue;
            }
            String toolId = token.trim().replaceAll("[^a-zA-Z0-9_-]", "");
            if (toolRegistry.containsToolId(toolId)) {
                selected.add(toolId);
            }
            if (selected.size() >= MAX_SELECTED_TOOLS) {
                break;
            }
        }

        if (selected.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(ToolSelectionResult.selected(new ArrayList<>(selected), "llm"));
    }

    private String buildSystemPrompt() {
        StringBuilder candidates = new StringBuilder();
        for (ToolMetadata metadata : toolRegistry.allMetadata()) {
            candidates.append("- ").append(metadata.id()).append(": ")
                    .append(metadata.summary()).append('\n');
            if (!metadata.selectionKeywords().isEmpty()) {
                candidates.append("  Keywords: ")
                        .append(String.join(", ", metadata.selectionKeywords()))
                        .append('\n');
            }
            if (!metadata.exampleQueries().isEmpty()) {
                candidates.append("  Examples: ")
                        .append(String.join(" | ", metadata.exampleQueries()))
                        .append('\n');
            }
        }

        return """
                You are a tool router for a chat assistant.
                Choose the minimal set of tools that may help answer the user's latest message.
                Return ONLY a comma-separated list of tool ids from the candidate list, or NONE.
                Select at most 3 tools.
                If the message can be answered directly without a tool, return NONE.
                If a tool is the right capability but the user omitted a required field, still select that tool.

                Candidate tools:
                %s
                """.formatted(candidates);
    }
}
