package com.ilink.test.ilinkcheck.application.tool.orchestration;

import java.util.List;

public record ToolExecutionBatch(
        ExecutionMode mode,
        List<ToolExecutionStep> steps,
        String reason
) {
    public ToolExecutionBatch {
        steps = steps == null ? List.of() : List.copyOf(steps);
        reason = reason == null ? "" : reason.trim();
    }
}
