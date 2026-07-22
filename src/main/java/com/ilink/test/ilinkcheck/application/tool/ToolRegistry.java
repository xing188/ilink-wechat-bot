package com.ilink.test.ilinkcheck.application.tool;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ToolRegistry {

    private final List<ChatFunctionTool> tools;

    public ToolRegistry(List<ChatFunctionTool> tools) {
        this.tools = List.copyOf(tools);
    }

    public Object[] toolObjects() {
        return tools.toArray(Object[]::new);
    }
}
