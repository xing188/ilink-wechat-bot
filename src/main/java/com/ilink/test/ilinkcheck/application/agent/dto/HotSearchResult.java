package com.ilink.test.ilinkcheck.application.agent.dto;

import java.util.List;

public record HotSearchResult(
        String source,
        String title,
        String summary,
        String url,
        int heatScore,
        List<String> relatedKeywords
) {}
