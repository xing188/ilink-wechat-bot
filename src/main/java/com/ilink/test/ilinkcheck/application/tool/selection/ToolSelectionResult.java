package com.ilink.test.ilinkcheck.application.tool.selection;

import java.util.LinkedHashSet;
import java.util.List;

public record ToolSelectionResult(
        List<String> toolIds,   // 选中的工具ID集合
        String strategy         // 本次工具选择使用的策略名称
) {

    public ToolSelectionResult {
        toolIds = toolIds == null ? List.of() : List.copyOf(new LinkedHashSet<>(toolIds));
        strategy = strategy == null || strategy.isBlank() ? "unknown" : strategy.trim();
    }

    public static ToolSelectionResult selected(List<String> toolIds, String strategy) {
        return new ToolSelectionResult(toolIds, strategy);
    }

    public static ToolSelectionResult noTools(String strategy) {
        return new ToolSelectionResult(List.of(), strategy);
    }

    public boolean hasTools() {
        return !toolIds.isEmpty();
    }
}
