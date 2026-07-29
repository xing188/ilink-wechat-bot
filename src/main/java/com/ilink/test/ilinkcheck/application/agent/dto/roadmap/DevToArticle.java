package com.ilink.test.ilinkcheck.application.agent.dto.roadmap;

public record DevToArticle(
        String title,
        String url,
        String tags,
        int positiveReactions,
        String description
) {}
