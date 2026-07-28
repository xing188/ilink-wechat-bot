package com.ilink.test.ilinkcheck.application.agent.spi.content;

import com.ilink.test.ilinkcheck.application.agent.dto.SentimentReport;

public interface SentimentAnalyzer {

    SentimentReport analyze(String text);
}
