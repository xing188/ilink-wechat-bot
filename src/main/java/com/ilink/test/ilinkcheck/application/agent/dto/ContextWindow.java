package com.ilink.test.ilinkcheck.application.agent.dto;

import java.util.List;

public record ContextWindow(
        String conversationId,
        List<String> recentInstructions,
        String personaSetting
) {}
