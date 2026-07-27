package com.ilink.test.ilinkcheck.application.tool.orchestration;

import java.util.List;

public record ToolOrchestrationResult(
        ToolExecutionPlan plan,
        List<ToolExecutionOutcome> outcomes,
        boolean usedParallelExecution,
        String finalReply
) {
    public ToolOrchestrationResult {
        outcomes = outcomes == null ? List.of() : List.copyOf(outcomes);
        finalReply = finalReply == null ? "" : finalReply.trim();
    }
}
