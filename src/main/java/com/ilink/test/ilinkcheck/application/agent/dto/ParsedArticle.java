package com.ilink.test.ilinkcheck.application.agent.dto;

import java.util.List;

public record ParsedArticle(
        String title,
        String content,
        String author,
        String platform,
        List<String> tags,
        String summary
) {}
