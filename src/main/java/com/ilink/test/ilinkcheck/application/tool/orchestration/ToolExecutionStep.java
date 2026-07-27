package com.ilink.test.ilinkcheck.application.tool.orchestration;

import java.util.Map;

public record ToolExecutionStep(
        String toolName,
        Map<String, Object> arguments,
        String purpose
) {
}
