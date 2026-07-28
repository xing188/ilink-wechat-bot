package com.ilink.test.ilinkcheck.application.tool.orchestration.model;

import java.util.List;

/**
 * 归一化后的 planner 结果。
 * 一次用户请求可能会经历多轮规划，每一轮都会产出一个 plan。
 *
 * @param batches 当前这一轮规划出的可执行批次列表
 * @param plannerNote planner 对本轮计划的备注
 */
public record ToolExecutionPlan(
        List<ToolExecutionBatch> batches,
        String plannerNote
) {
    public ToolExecutionPlan {
        batches = batches == null ? List.of() : List.copyOf(batches);
        plannerNote = plannerNote == null ? "" : plannerNote.trim();
    }

    /**
     * 只有至少一个批次里还存在步骤时，这个 plan 才算可执行。
     *
     * @return 是否存在至少一个可执行步骤
     */
    public boolean hasSteps() {
        return batches.stream().anyMatch(batch -> !batch.steps().isEmpty());
    }
}
