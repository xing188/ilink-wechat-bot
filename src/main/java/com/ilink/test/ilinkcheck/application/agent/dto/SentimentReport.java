package com.ilink.test.ilinkcheck.application.agent.dto;

import java.util.List;

public record SentimentReport(
        String overallSentiment,
        double positiveRatio,
        double negativeRatio,
        List<String> painPoints,
        List<String> controversyPoints
) {}
