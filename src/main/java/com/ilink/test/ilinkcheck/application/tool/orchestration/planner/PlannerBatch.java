package com.ilink.test.ilinkcheck.application.tool.orchestration.planner;

import java.util.List;
import java.util.Map;

/**
 * 模型返回的原始批次 DTO。
 * 这里还是“planner 语言”，后续还要再归一化成真正可执行的批次。
 *
 * @param mode 模型给出的执行模式，理论值是 SERIAL 或 PARALLEL
 * @param reason 模型认为需要这一批次的原因说明
 * @param steps 当前批次里的原始步骤列表
 */
public record PlannerBatch(
        String mode,
        String reason,
        List<PlannerStep> steps
) {

    /**
     * 模型返回的原始步骤 DTO。
     *
     * @param toolName 模型计划调用的 callback 名称
     * @param purpose 这一步的业务目的说明
     * @param arguments 调用该工具时准备传入的原始参数
     */
    public record PlannerStep(
            String toolName,
            String purpose,
            Map<String, Object> arguments
    ) {
    }
}
