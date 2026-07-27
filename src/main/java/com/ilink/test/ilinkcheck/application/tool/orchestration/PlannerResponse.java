package com.ilink.test.ilinkcheck.application.tool.orchestration;

import java.util.List;

public record PlannerResponse(
        String note,
        List<PlannerBatch> batches
) {
}
