package com.ilink.test.ilinkcheck.application.tool;

/**
 * Marker interface for chat tools exposed to the model.
 */
public interface ChatFunctionTool {

    default ToolMetadata metadata() {
        return new ToolMetadata(
                getClass().getSimpleName(),
                "General-purpose tool",
                java.util.List.of(),
                java.util.List.of(),
                "Use this tool only when it is clearly the best match."
        );
    }
}
