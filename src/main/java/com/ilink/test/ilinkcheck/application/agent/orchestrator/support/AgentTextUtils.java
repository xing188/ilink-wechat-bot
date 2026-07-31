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
    // 注意：提示词按"最长优先"排列（学习路线 / 路线规划 放在 学习 前面），
    // 避免 "学习路线 eBPF" 被误摘成主题 "路线"。
    // "我想学" 用 (?!习) 负向前瞻防止把 "我想学习 xxx" 的 "习" 吃进主题组
    // （否则正则先匹配较短的"我想学"，剩余"习xxx"全变成主题）。
    // 主题字符类在 ASCII 基础上额外支持中文（\u4e00-\u9fa5），并允许单个空格分隔的多词主题（如 Spring AI）。
    private static final Pattern LEARN_TOPIC = Pattern.compile("(?:学习路线|路线规划|我想学(?!习)|学习|入门)\\s*([A-Za-z0-9+#._\\u4e00-\\u9fa5]+(?:\\s[A-Za-z0-9+#._\\u4e00-\\u9fa5]+)*)");
    private static final Pattern TIME_PATTERN = Pattern.compile("\\b([01]?\\d|2[0-3]):([0-5]\\d)\\b");

    // 翻译意图关键词：用户希望把英文资料翻译成中文时使用的表达。
    // 用正则整体匹配，避免"中文"这类子串在其它语境下误触发（如"中文文档"属正常主题）。
    private static final Pattern TRANSLATE_HINTS = Pattern.compile("翻译成中文|译成中文|中文版|翻译|中文|汉化");

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

        // 先剥离翻译意图关键词（如"翻译成中文"），避免它污染主题提取。
        String cleaned = TRANSLATE_HINTS.matcher(text).replaceAll("").trim();

        Matcher learnMatcher = LEARN_TOPIC.matcher(cleaned);
        if (learnMatcher.find()) {
            String rawTopic = learnMatcher.group(1).trim();
            // 过滤掉关键词后缀词（如"学习路线"被拆出"路线"、"路线规划"被拆出"规划"），
            // 这些不是真正的学习主题，应交给后续候选列表兜底。
            if (!Set.of("路线", "规划").contains(rawTopic)) {
                // 清理前后粘连的中文字符：当主题是"中文+英文混合"（如"习rag"、"rag版"），
                // 说明正则把前缀/后缀的中文字吃进了主题组，需剥离只保留英文/数字部分。
                String topic = stripEdgeChinese(rawTopic);
                // 规范化为专业术语写法（rag → RAG、ebpf → eBPF），保证输出用词一致。
                return Optional.of(canonicalize(topic));
            }
        }

        for (String candidate : CANONICAL_TOPICS.values()) {
            if (cleaned.contains(candidate)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    /**
     * 判断用户消息是否希望把资料翻译成中文。
     * 用于驱动路线回复的"中文化"渲染（如"我想学 RAG 翻译成中文"）。
     */
    public static boolean wantsChinese(String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        return TRANSLATE_HINTS.matcher(text).find();
    }

    /**
     * 把原始主题规范为专业术语写法。大小写不敏感；不在规范表中的主题原样返回。
     */
    private static String canonicalize(String raw) {
        return CANONICAL_TOPICS.getOrDefault(raw.toLowerCase(), raw);
    }

    /**
     * 剥离主题首尾粘连的中文字符。
     * <p>
     * 当正则前缀/后缀（如"我想学"的"习"、"中文版"的"版"）与主题无空格粘连时，
     * 中文字会被吃进主题组，需要剥掉只保留英文/数字：
     * "习rag" → "rag"；"rag版" → "rag"；"rag翻译成中文" → "rag"。
     * <p>
     * 纯中文主题（如"微服务"）保持不变。
     */
    private static String stripEdgeChinese(String raw) {
        if (raw == null || raw.isEmpty()) {
            return raw;
        }
        boolean hasAscii = raw.chars().anyMatch(c -> c <= 0x7E && !Character.isWhitespace(c));
        if (!hasAscii) {
            return raw; // 纯中文主题，原样保留
        }
        String s = raw;
        // 剥离前导中文字符
        int i = 0;
        while (i < s.length() && s.charAt(i) >= 0x4E00 && s.charAt(i) <= 0x9FFF) {
            i++;
        }
        s = s.substring(i);
        // 剥离尾随中文字符
        int j = s.length();
        while (j > 0 && s.charAt(j - 1) >= 0x4E00 && s.charAt(j - 1) <= 0x9FFF) {
            j--;
        }
        s = s.substring(0, j);
        return s.trim();
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
