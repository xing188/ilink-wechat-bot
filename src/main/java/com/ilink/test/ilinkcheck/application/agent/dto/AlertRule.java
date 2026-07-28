package com.ilink.test.ilinkcheck.application.agent.dto;

public record AlertRule(
        String userId,
        String keyword,
        int threshold,
        boolean enabled
) {}
