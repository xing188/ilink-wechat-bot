package com.ilink.test.ilinkcheck.application.agent.dto.github;

public record RepoInfo(
        String owner,
        String name,
        String description,
        String language,
        int stars,
        int forks,
        String readmeSummary,
        String techStack,
        String readmeRaw
) {}
