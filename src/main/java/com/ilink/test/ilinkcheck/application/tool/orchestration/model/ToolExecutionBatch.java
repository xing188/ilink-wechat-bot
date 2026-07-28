package com.ilink.test.ilinkcheck.application.tool.orchestration.model;

import java.util.List;

/**
 * 归一化后的可执行批次。
 * 一个批次是判断“该串行还是并行”的最小单位。
 *
 * @param mode 当前批次最终采用的执行模式
 * @param steps 当前批次中需要执行的步骤列表
 * @param reason 当前批次存在的原因说明，主要用于日志和总结
 */
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
