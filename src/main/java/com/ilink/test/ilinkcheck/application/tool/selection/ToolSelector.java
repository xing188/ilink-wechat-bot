package com.ilink.test.ilinkcheck.application.tool.selection;

public interface ToolSelector {

    ToolSelectionResult select(String conversationId, String userMessage);
}
