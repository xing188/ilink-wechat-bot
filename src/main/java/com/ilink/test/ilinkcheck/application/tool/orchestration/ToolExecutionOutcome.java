package com.ilink.test.ilinkcheck.application.tool.orchestration;

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
