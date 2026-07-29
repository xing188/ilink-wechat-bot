package com.ilink.test.ilinkcheck.application.agent.dto.github;

public record TrendingRepo(
        String owner,
        String name,
        String description,
        String language,
        int starsToday,
        String url
) {}
