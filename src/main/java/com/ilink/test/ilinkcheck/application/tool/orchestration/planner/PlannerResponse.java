package com.ilink.test.ilinkcheck.application.tool.orchestration.planner;

import java.util.List;

/**
 * 模型 JSON 输出对应的顶层 DTO。
 *
 * @param note 模型对本轮规划的简短备注
 * @param batches 模型给出的原始批次列表
 */
public record PlannerResponse(
        String note,
        List<PlannerBatch> batches
) {
}
