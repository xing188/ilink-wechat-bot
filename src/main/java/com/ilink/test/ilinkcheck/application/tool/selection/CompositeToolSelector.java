package com.ilink.test.ilinkcheck.application.tool.selection;

import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class CompositeToolSelector implements ToolSelector {

    private final RuleBasedToolSelector ruleBasedToolSelector;
    private final LlmToolSelector llmToolSelector;

    public CompositeToolSelector(RuleBasedToolSelector ruleBasedToolSelector,
                                 LlmToolSelector llmToolSelector) {
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

        return ToolSelectionResult.noTools("fallback-none");
    }
}
