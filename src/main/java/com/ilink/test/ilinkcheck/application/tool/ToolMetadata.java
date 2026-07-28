package com.ilink.test.ilinkcheck.application.tool;

import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public record ToolMetadata(
        String id,
        String summary,
        List<String> selectionKeywords,
        List<String> exampleQueries,
        String routingInstruction
) {

    public ToolMetadata {
        if (!StringUtils.hasText(id)) {
            throw new IllegalArgumentException("tool id must not be blank");
        }
        if (!StringUtils.hasText(summary)) {
            throw new IllegalArgumentException("tool summary must not be blank");
        }
        selectionKeywords = normalizeList(selectionKeywords);
        exampleQueries = normalizeList(exampleQueries);
        routingInstruction = StringUtils.hasText(routingInstruction)
                ? routingInstruction.trim()
                : "Use this tool only when it clearly matches the user's intent.";
    }

    public int matchScore(String text) {
        if (!StringUtils.hasText(text) || selectionKeywords.isEmpty()) {
            return 0;
        }

        String normalizedText = normalize(text);
        int score = 0;
        for (String keyword : selectionKeywords) {
            if (normalizedText.contains(normalize(keyword))) {
                score++;
            }
        }
        return score;
    }

    private static List<String> normalizeList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }

        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                unique.add(value.trim());
            }
        }
        return List.copyOf(unique);
    }

    private static String normalize(String text) {
        return Objects.requireNonNullElse(text, "")
                .trim()
                .toLowerCase(Locale.ROOT);
    }
}
