package com.ilink.test.ilinkcheck.application.agent.dto.roadmap;

import java.util.List;

public record RoadmapPlan(
        String topic,
        List<String> prerequisites,
        List<String> coreConcepts,
        List<String> projectIdeas,
        List<DevToArticle> recommendedArticles
) {}
