package com.ilink.test.ilinkcheck.application.agent.dto.review;

public record ReviewSuggestion(
        String type,
        String location,
        String message,
        String suggestion
) {}
