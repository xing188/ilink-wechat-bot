package com.ilink.test.ilinkcheck.application.agent.orchestrator.support;

import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
    // 注意：提示词按“最长优先”排列（学习路线 / 路线规划 放在 学习 前面），
    // 避免 “学习路线 eBPF” 被误摘成主题 “路线”。
    // 主题前缀与主题之间用 \\s*（零或多个空白），兼容中文无空格输入如"我想学微服务"。
    // 主题字符类在 ASCII 基础上额外支持中文（\\u4e00-\\u9fa5），并允许单个空格分隔的多词主题（如 Spring AI）。
    private static final Pattern LEARN_TOPIC = Pattern.compile("(?:学习路线|路线规划|我想学|学习|入门)\\s*([A-Za-z0-9+#._\\u4e00-\\u9fa5]+(?:\\s[A-Za-z0-9+#._\\u4e00-\\u9fa5]+)*)");
    private static final Pattern TIME_PATTERN = Pattern.compile("\\b([01]?\\d|2[0-3]):([0-5]\\d)\\b");

    // 专业术语规范表：用户输入的小写 / 大小写混写（如 rag / ebpf）统一规范为专业写法（RAG / eBPF）。
    // key 统一小写，便于大小写不敏感匹配；value 是展示用的规范形式。
    // 不在表中的主题（如中文"微服务"）保持原样，原样输出即为规范写法。
    private static final Map<String, String> CANONICAL_TOPICS = Map.of(
            "rag", "RAG",
            "ebpf", "eBPF",
            "mcp", "MCP",
            "spring ai", "Spring AI",
            "langgraph", "LangGraph",
            "opentelemetry", "OpenTelemetry"
    );

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
            String topic = learnMatcher.group(1).trim();
            // 过滤掉关键词后缀词（如"学习路线"被拆出"路线"、"路线规划"被拆出"规划"），
            // 这些不是真正的学习主题，应交给后续候选列表兜底。
            if (!Set.of("路线", "规划").contains(topic)) {
                // 规范化为专业术语写法（rag → RAG、ebpf → eBPF），保证输出用词一致。
                return Optional.of(canonicalize(topic));
            }
        }

        for (String candidate : CANONICAL_TOPICS.values()) {
            if (text.contains(candidate)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    /**
     * 把原始主题规范为专业术语写法。大小写不敏感；不在规范表中的主题原样返回。
     */
    private static String canonicalize(String raw) {
        return CANONICAL_TOPICS.getOrDefault(raw.toLowerCase(), raw);
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
