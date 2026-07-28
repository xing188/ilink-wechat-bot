package com.ilink.test.ilinkcheck.application.tool.orchestration.model;

/**
 * 记录一个工具步骤的执行结果，包含成功输出或失败信息。
 *
 * @param step 对应的执行步骤
 * @param output 工具成功执行后的文本输出
 * @param success 该步骤是否执行成功
 * @param errorMessage 如果失败，对应的错误信息
 */
public record ToolExecutionOutcome(
        ToolExecutionStep step,
        String output,
        boolean success,
        String errorMessage
) {

    public static ToolExecutionOutcome success(ToolExecutionStep step, String output) {
        return new ToolExecutionOutcome(step, output, true, "");
    }

    public static ToolExecutionOutcome failure(ToolExecutionStep step, String errorMessage) {
        return new ToolExecutionOutcome(step, "", false, errorMessage == null ? "" : errorMessage);
    }
}
