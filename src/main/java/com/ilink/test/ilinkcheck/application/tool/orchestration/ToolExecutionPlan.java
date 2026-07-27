package com.ilink.test.ilinkcheck.application.tool.orchestration;

import java.util.List;

public record ToolExecutionPlan(
        List<ToolExecutionBatch> batches,
        String plannerNote
) {
    public ToolExecutionPlan {
        batches = batches == null ? List.of() : List.copyOf(batches);
        plannerNote = plannerNote == null ? "" : plannerNote.trim();
    }

    public boolean hasSteps() {
        return batches.stream().anyMatch(batch -> !batch.steps().isEmpty());
    }
}
