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
    private static final Pattern CHINESE_TIME_PATTERN = Pattern.compile(
            "(?:(?<day>今天|明天|后天|每天|每日)\\s*)?"
                    + "(?<period>凌晨|清晨|今晨|今早|早晨|早上|上午|中午|下午|傍晚|晚上|今晚|晚间)?\\s*"
                    + "(?<hour>[0-9零〇一二两三四五六七八九十]+)\\s*(?:点|时)"
                    + "(?:\\s*(?<minute>[0-9零〇一二两三四五六七八九十]+|半)\\s*(?:分|分钟)?)?"
    );
    private static final Pattern PERIOD_ONLY_PATTERN = Pattern.compile("凌晨|清晨|今晨|今早|早晨|早上|上午|中午|下午|傍晚|晚上|今晚|晚间");

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

    public enum ScheduleTimeStatus {
        RESOLVED,
        AMBIGUOUS,
        ABSENT
    }

    public record ScheduleTimeParseResult(
            ScheduleTimeStatus status,
            String normalizedTime,
            String matchedText,
            Integer hour,
            Integer minute
    ) {

        public boolean resolved() {
            return status == ScheduleTimeStatus.RESOLVED && StringUtils.hasText(normalizedTime);
        }

        public boolean ambiguous() {
            return status == ScheduleTimeStatus.AMBIGUOUS;
        }
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
        ScheduleTimeParseResult parsed = parseScheduleTime(text);
        return parsed.resolved() ? Optional.of(parsed.normalizedTime()) : Optional.empty();
    }

    /**
     * 解析晨报订阅时间。
     * 支持 24 小时制（08:30）和中文自然表达（早上九点、下午三点半）。
     * 若仅说“9点推送”这类无法判断上午/下午的表达，则返回 AMBIGUOUS，让上层继续追问。
     */
    public static ScheduleTimeParseResult parseScheduleTime(String text) {
        if (!StringUtils.hasText(text)) {
            return new ScheduleTimeParseResult(ScheduleTimeStatus.ABSENT, null, null, null, null);
        }

        Matcher exactMatcher = TIME_PATTERN.matcher(text);
        if (exactMatcher.find()) {
            return new ScheduleTimeParseResult(
                    ScheduleTimeStatus.RESOLVED,
                    exactMatcher.group(),
                    exactMatcher.group(),
                    Integer.parseInt(exactMatcher.group(1)),
                    Integer.parseInt(exactMatcher.group(2))
            );
        }

        Matcher chineseMatcher = CHINESE_TIME_PATTERN.matcher(text);
        if (!chineseMatcher.find()) {
            return new ScheduleTimeParseResult(ScheduleTimeStatus.ABSENT, null, null, null, null);
        }

        int hour = parseChineseNumber(chineseMatcher.group("hour"));
        String minuteText = chineseMatcher.group("minute");
        int minute = "半".equals(minuteText) ? 30 : (StringUtils.hasText(minuteText) ? parseChineseNumber(minuteText) : 0);
        if (minute < 0 || minute > 59 || hour < 0 || hour > 23) {
            return new ScheduleTimeParseResult(ScheduleTimeStatus.ABSENT, null, chineseMatcher.group(), hour, minute);
        }

        String period = normalizeSchedulePeriod(chineseMatcher.group("period")).orElse(null);
        if (!StringUtils.hasText(period) && hour >= 1 && hour <= 12) {
            return new ScheduleTimeParseResult(ScheduleTimeStatus.AMBIGUOUS, null, chineseMatcher.group(), hour, minute);
        }

        int normalizedHour = to24Hour(hour, period);
        if (normalizedHour < 0 || normalizedHour > 23) {
            return new ScheduleTimeParseResult(ScheduleTimeStatus.ABSENT, null, chineseMatcher.group(), hour, minute);
        }
        return new ScheduleTimeParseResult(
                ScheduleTimeStatus.RESOLVED,
                "%02d:%02d".formatted(normalizedHour, minute),
                chineseMatcher.group(),
                hour,
                minute
        );
    }

    /**
     * 提取“上午/下午/晚上”这类时段补充。
     * 用于接住上一轮已经识别出“9点”，下一轮只回复“上午/下午”的场景。
     */
    public static Optional<String> extractSchedulePeriod(String text) {
        if (!StringUtils.hasText(text)) {
            return Optional.empty();
        }
        Matcher matcher = PERIOD_ONLY_PATTERN.matcher(text);
        if (!matcher.find()) {
            return Optional.empty();
        }
        return normalizeSchedulePeriod(matcher.group());
    }

    /**
     * 将 12 小时制候选时间和用户补充的上午/下午拼成最终 HH:mm。
     */
    public static Optional<String> composeScheduleTime(Integer hour, Integer minute, String period) {
        if (hour == null || minute == null || !StringUtils.hasText(period)) {
            return Optional.empty();
        }
        if (hour < 1 || hour > 12 || minute < 0 || minute > 59) {
            return Optional.empty();
        }
        int normalizedHour = to24Hour(hour, period);
        return Optional.of("%02d:%02d".formatted(normalizedHour, minute));
    }

    /**
     * 判断用户消息是否为目录路径（如 main、src/main、docs/guide/）。
     * 用于 GitHub 目录架构分析完成后，识别"回复目录名继续展开"的后续指令。
     */
    public static boolean isDirectoryPath(String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        String normalized = text.trim().replaceAll("/+$", "");
        return !normalized.isEmpty() && normalized.matches("[A-Za-z0-9._-]+(/[A-Za-z0-9._-]+)*");
    }

    /**
     * 从用户消息中提取目录路径（去掉末尾斜杠）；非目录路径返回 empty。
     */
    public static Optional<String> extractDirectoryPath(String text) {
        if (!StringUtils.hasText(text)) {
            return Optional.empty();
        }
        String normalized = text.trim().replaceAll("/+$", "");
        if (normalized.isEmpty() || !normalized.matches("[A-Za-z0-9._-]+(/[A-Za-z0-9._-]+)*")) {
            return Optional.empty();
        }
        return Optional.of(normalized);
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

    private static Optional<String> normalizeSchedulePeriod(String rawPeriod) {
        if (!StringUtils.hasText(rawPeriod)) {
            return Optional.empty();
        }
        return Optional.of(switch (rawPeriod.trim()) {
            case "清晨", "今晨", "今早", "早晨", "早上" -> "上午";
            case "晚间", "今晚", "傍晚" -> "晚上";
            default -> rawPeriod.trim();
        });
    }

    private static int to24Hour(int hour, String period) {
        if (!StringUtils.hasText(period)) {
            return hour;
        }
        return switch (period) {
            case "凌晨" -> hour == 12 ? 0 : hour;
            case "上午" -> hour == 12 ? 0 : hour;
            case "中午" -> hour == 12 ? 12 : hour + 12;
            case "下午" -> hour == 12 ? 12 : hour + 12;
            case "晚上" -> hour == 12 ? 0 : hour + 12;
            default -> hour;
        };
    }

    private static int parseChineseNumber(String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("时间数字不能为空");
        }

        String normalized = value.replace('两', '二').replace("〇", "零").trim();
        if (normalized.matches("\\d+")) {
            return Integer.parseInt(normalized);
        }

        return switch (normalized) {
            case "零" -> 0;
            case "一" -> 1;
            case "二" -> 2;
            case "三" -> 3;
            case "四" -> 4;
            case "五" -> 5;
            case "六" -> 6;
            case "七" -> 7;
            case "八" -> 8;
            case "九" -> 9;
            case "十" -> 10;
            default -> parseChineseTens(normalized);
        };
    }

    private static int parseChineseTens(String value) {
        if (!value.contains("十")) {
            throw new IllegalArgumentException("暂时只支持 0-99 的中文数字");
        }

        String[] parts = value.split("十", -1);
        int tens = StringUtils.hasText(parts[0]) ? parseSingleChineseDigit(parts[0]) : 1;
        int ones = parts.length > 1 && StringUtils.hasText(parts[1]) ? parseSingleChineseDigit(parts[1]) : 0;
        return tens * 10 + ones;
    }

    private static int parseSingleChineseDigit(String value) {
        return switch (value) {
            case "零" -> 0;
            case "一" -> 1;
            case "二" -> 2;
            case "三" -> 3;
            case "四" -> 4;
            case "五" -> 5;
            case "六" -> 6;
            case "七" -> 7;
            case "八" -> 8;
            case "九" -> 9;
            default -> throw new IllegalArgumentException("暂时只支持 0-99 的中文数字");
        };
    }
}
