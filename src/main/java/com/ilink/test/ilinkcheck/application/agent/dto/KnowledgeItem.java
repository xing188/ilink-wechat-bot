package com.ilink.test.ilinkcheck.application.agent.dto;

public record KnowledgeItem(
        String id,
        String title,
        String content,
        String category,
        String style,
        float score
) {}
