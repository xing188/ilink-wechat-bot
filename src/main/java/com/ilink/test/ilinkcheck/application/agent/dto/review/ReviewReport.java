package com.ilink.test.ilinkcheck.application.agent.dto.review;

import java.util.List;

public record ReviewReport(
        boolean passed,
        List<ReviewSuggestion> suggestions,
        String summary
) {}
