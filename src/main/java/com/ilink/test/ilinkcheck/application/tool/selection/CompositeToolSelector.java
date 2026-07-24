package com.ilink.test.ilinkcheck.application.tool.selection;

import com.ilink.test.ilinkcheck.application.tool.ToolRegistry;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class CompositeToolSelector implements ToolSelector {

    private final ToolRegistry toolRegistry;
    private final RuleBasedToolSelector ruleBasedToolSelector;
    private final LlmToolSelector llmToolSelector;

    public CompositeToolSelector(ToolRegistry toolRegistry,
                                 RuleBasedToolSelector ruleBasedToolSelector,
                                 LlmToolSelector llmToolSelector) {
        this.toolRegistry = toolRegistry;
        this.ruleBasedToolSelector = ruleBasedToolSelector;
        this.llmToolSelector = llmToolSelector;
    }

    @Override
    public ToolSelectionResult select(String conversationId, String userMessage) {
        Optional<ToolSelectionResult> ruleResult = ruleBasedToolSelector.trySelect(userMessage);
        if (ruleResult.isPresent()) {
            return ruleResult.get();
        }

        Optional<ToolSelectionResult> llmResult = llmToolSelector.trySelect(userMessage);
        if (llmResult.isPresent()) {
            return llmResult.get();
        }

        return ToolSelectionResult.selected(toolRegistry.allToolIds(), "fallback-all");
    }
}
