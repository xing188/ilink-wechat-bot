package com.ilink.test.ilinkcheck.application.tool.orchestration;

import java.util.List;
import java.util.Map;

public record PlannerBatch(
        String mode,
        String reason,
        List<PlannerStep> steps
) {

    public record PlannerStep(
            String toolName,
            String purpose,
            Map<String, Object> arguments
    ) {
    }
}
