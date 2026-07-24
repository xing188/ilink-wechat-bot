package com.ilink.test.ilinkcheck.application.tool.selection;

import com.ilink.test.ilinkcheck.application.tool.ToolMetadata;
import com.ilink.test.ilinkcheck.application.tool.ToolRegistry;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class RuleBasedToolSelector {

    private static final int MAX_RULE_TOOLS = 3;

    private final ToolRegistry toolRegistry;

    public RuleBasedToolSelector(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    public Optional<ToolSelectionResult> trySelect(String userMessage) {
        if (!StringUtils.hasText(userMessage)) {
            return Optional.empty();
        }

        List<Map.Entry<ToolMetadata, Integer>> matches = toolRegistry.allMetadata().stream()
                .map(metadata -> Map.entry(metadata, metadata.matchScore(userMessage)))
                .filter(entry -> entry.getValue() > 0)
                .sorted(Comparator.<Map.Entry<ToolMetadata, Integer>>comparingInt(Map.Entry::getValue)
                        .reversed())
                .limit(MAX_RULE_TOOLS)
                .toList();

        if (matches.isEmpty()) {
            return Optional.empty();
        }

        List<String> selectedToolIds = matches.stream()
                .map(entry -> entry.getKey().id())
                .toList();
        return Optional.of(ToolSelectionResult.selected(selectedToolIds, "rule"));
    }
}
