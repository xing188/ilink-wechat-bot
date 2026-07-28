package com.ilink.test.ilinkcheck.application.agent.dto;

import java.util.List;

public record FactCheckReport(
        boolean passed,
        List<String> issues,
        String suggestion
) {}
