package com.ilink.test.ilinkcheck.application.tool.orchestration.model;

import java.util.List;

/**
 * 返回给对话层的最终编排结果。
 *
 * @param plan 最终汇总后的执行计划
 * @param outcomes 所有已经执行过的步骤结果
 * @param usedParallelExecution 这次请求里是否真实使用过并行执行
 * @param finalReply 最终返回给用户的文本回复
 */
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
