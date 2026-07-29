package com.ilink.test.ilinkcheck.application.agent.orchestrator.support;

import org.springframework.util.StringUtils;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Agent 文本解析工具。
 * 把高频输入提取逻辑收口到一个地方，避免每个阶段执行器都重复写正则。
 */
public final class AgentTextUtils {

    private static final Pattern GITHUB_URL = Pattern.compile("https?://github\\.com/([^\\s/]+)/([^\\s?#/]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern REPOSITORY_NAME = Pattern.compile("\\b([A-Za-z0-9_.-]+)/([A-Za-z0-9_.-]+)\\b");
    private static final Pattern QUOTED_TEXT = Pattern.compile("[“\"']([^”\"']+)[”\"']");
    private static final Pattern LEARN_TOPIC = Pattern.compile("(?:我想学|学习|入门|路线规划|学习路线)\\s*([A-Za-z0-9+#._-]+)");
    private static final Pattern TIME_PATTERN = Pattern.compile("\\b([01]?\\d|2[0-3]):([0-5]\\d)\\b");

    private AgentTextUtils() {
    }

    /**
     * 从用户输入中提取 GitHub 仓库 owner/repo。
     */
    public static Optional<String[]> extractRepository(String text) {
        if (!StringUtils.hasText(text)) {
            return Optional.empty();
        }

        Matcher urlMatcher = GITHUB_URL.matcher(text);
        if (urlMatcher.find()) {
            return Optional.of(new String[]{urlMatcher.group(1), urlMatcher.group(2)});
        }

        Matcher nameMatcher = REPOSITORY_NAME.matcher(text);
        while (nameMatcher.find()) {
            String owner = nameMatcher.group(1);
            String repo = nameMatcher.group(2);
            if (!owner.contains(":") && !repo.contains(":")) {
                return Optional.of(new String[]{owner, repo});
            }
        }
        return Optional.empty();
    }

    /**
     * 从用户输入中提取学习主题。
     */
    public static Optional<String> extractTopic(String text) {
        if (!StringUtils.hasText(text)) {
            return Optional.empty();
        }

        Matcher quotedMatcher = QUOTED_TEXT.matcher(text);
        if (quotedMatcher.find()) {
            return Optional.of(quotedMatcher.group(1).trim());
        }

        Matcher learnMatcher = LEARN_TOPIC.matcher(text);
        if (learnMatcher.find()) {
            return Optional.of(learnMatcher.group(1).trim());
        }

        for (String candidate : new String[]{"eBPF", "RAG", "MCP", "Spring AI", "LangGraph", "OpenTelemetry"}) {
            if (text.contains(candidate)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    /**
     * 从用户输入中提取晨报订阅时间。
     */
    public static Optional<String> extractScheduleTime(String text) {
        if (!StringUtils.hasText(text)) {
            return Optional.empty();
        }
        Matcher matcher = TIME_PATTERN.matcher(text);
        if (!matcher.find()) {
            return Optional.empty();
        }
        return Optional.of(matcher.group());
    }

    /**
     * 判断用户消息里是否已经包含可审稿的正文。
     */
    public static Optional<String> extractDraftText(String text) {
        if (!StringUtils.hasText(text)) {
            return Optional.empty();
        }
        String normalized = text.trim();
        if (normalized.length() >= 80 || normalized.contains("\n") || normalized.contains("```")) {
            return Optional.of(normalized);
        }
        return Optional.empty();
    }
}
