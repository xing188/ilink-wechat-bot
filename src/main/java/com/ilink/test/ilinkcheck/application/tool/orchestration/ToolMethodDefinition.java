package com.ilink.test.ilinkcheck.application.tool.orchestration;

import org.springframework.ai.tool.ToolCallback;

public record ToolMethodDefinition(
        String toolId,
        String callbackName,
        ToolCallback callback,
        boolean readOnly,
        String description,
        String inputSchema
) {
}
