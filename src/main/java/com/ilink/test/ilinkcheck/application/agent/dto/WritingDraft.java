package com.ilink.test.ilinkcheck.application.agent.dto;

import java.util.List;

public record WritingDraft(
        String title,
        String content,
        String platform,
        List<String> suggestedTags,
        int qualityScore
) {}
