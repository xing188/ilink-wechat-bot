package com.ilink.test.ilinkcheck.application.agent.orchestrator.routing;

import com.ilink.test.ilinkcheck.application.agent.orchestrator.model.AgentTaskType;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.support.AgentTextUtils;
import com.ilink.test.ilinkcheck.infrastructure.common.TimeoutExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Agent 意图路由器
 * 职责：判断用户消息是否需要启动指定 Agent 工作流。
 *
 * <p>匹配策略（LLM 语义路由优先 + 关键词规则兜底）：</p>
 * <ol>
 *   <li>高确定性信号短路：GitHub 链接 → {@code GITHUB_REPO_DIGEST}，省一次 LLM 调用；</li>
 *   <li>LLM 语义路由：注入 {@code routingChatClient} 时调用模型判断意图，
 *       返回结构化 JSON。LLM 判定命中任务直接采用；判定为 {@code NONE} 时信任 LLM 直接短路；</li>
 *   <li>关键词规则兜底：LLM 未配置、超时、调用失败或输出无法解析时，
 *       回退到原有的关键词匹配，保证旧行为不退化。</li>
 * </ol>
 *
 * <p>超时控制：LLM 路由通过 {@link TimeoutExecutor} 限时等待，
 * 超时后取消任务并按规则兜底，不会拖慢普通对话链路。</p>
 */
@Slf4j
@Service
public class IntentRouter {

    // Github 项目速读触发关键词（规则兜底用）
    private static final List<String> GITHUB_HINTS = List.of("github", "仓库", "repo", "readme", "开源项目", "速读");
    private static final List<String> ROADMAP_HINTS = List.of("学习路线", "路线规划", "我想学", "怎么学", "学习", "入门", "roadmap");
    private static final List<String> REVIEW_HINTS = List.of("审稿", "审一下", "博客草稿", "文档规范", "排版", "review");
    // 技术晨报订阅关键词
    private static final List<String> DIGEST_HINTS = List.of("晨报", "日报", "订阅", "推送", "每天", "digest");

    /** 从 LLM 输出中提取 task 字段：{"task": "TECH_ROADMAP"} 或 {"task":"NONE"} */
    private static final Pattern TASK_TOKEN = Pattern.compile(
            "\"task\"\\s*[:=]\\s*\"?([A-Za-z_]+)\"?", Pattern.CASE_INSENSITIVE);

    /** LLM 语义路由最长等待时间（毫秒），超时按规则兜底 */
    private long llmTimeoutMs = 3000;

    /** LLM 语义路由开关；关闭后完全走关键词规则 */
    private boolean llmEnabled = true;

    /** 语义路由用 ChatClient（可选）。来自 {@code routingChatClient} Bean；未注入时只走规则。 */
    private ChatClient routingChatClient;

    @Autowired(required = false)
    public void setRoutingChatClient(@Qualifier("routingChatClient") ChatClient routingChatClient) {
        this.routingChatClient = routingChatClient;
    }

    @Value("${app.agent.intent-llm-timeout-ms:3000}")
    public void setLlmTimeoutMs(long llmTimeoutMs) {
        this.llmTimeoutMs = llmTimeoutMs;
    }

    @Value("${app.agent.intent-llm-enabled:true}")
    public void setLlmEnabled(boolean llmEnabled) {
        this.llmEnabled = llmEnabled;
    }

    public Optional<AgentTaskType> route(String userMessage) {
        if (!StringUtils.hasText(userMessage)) {
            return Optional.empty();
        }
        String text = userMessage.trim();

        // 优先级1：GitHub 链接 —— 高确定性信号，直接短路，不浪费一次 LLM 调用
        if (hasGithubUrl(text)) {
            log.info("IntentRouter route result, message={}, matchTask=GITHUB_REPO_DIGEST, strategy=github-url", text);
            return Optional.of(AgentTaskType.GITHUB_REPO_DIGEST);
        }

        // 优先级2：LLM 语义路由
        if (llmEnabled && routingChatClient != null) {
            LlmVerdict verdict = llmRoute(text);
            if (verdict.decision() == LlmVerdict.LlmDecision.TASK) {
                log.info("IntentRouter route result, message={}, matchTask={}, strategy=llm", text, verdict.task());
                return Optional.of(verdict.task());
            }
            if (verdict.decision() == LlmVerdict.LlmDecision.NONE) {
                log.info("IntentRouter route result, message={}, matchTask=NONE, strategy=llm-none", text);
                return Optional.empty();
            }
            log.info("IntentRouter route result, message={}, llmDegraded=true, strategy=rule-fallback", text);
        }

        // 优先级3：关键词规则兜底（含长文本草稿）
        Optional<AgentTaskType> result = ruleRoute(text);
        log.info("IntentRouter route result, message={}, matchTask={}, strategy={}",
                text, result.map(AgentTaskType::name).orElse("NONE"),
                (llmEnabled && routingChatClient != null) ? "rule-fallback" : "rule");
        return result;
    }

    /**
     * 调用 LLM 判定意图。任何异常（未配置 key、网络失败、超时）都按降级处理。
     * protected 便于测试覆写。
     */
    protected LlmVerdict llmRoute(String text) {
        String content = TimeoutExecutor.executeOrFallback(
                () -> routingChatClient.prompt()
                        .system(buildSystemPrompt())
                        .user("用户消息：\n" + text)
                        .call()
                        .content(),
                llmTimeoutMs,
                "",
                "意图路由");
        return parseLlmResponse(content);
    }

    /**
     * 解析 LLM 输出为 {@link LlmVerdict}。
     * 兼容 {"task":"XXX"} JSON、```json 围栏以及裸枚举名/NONE。
     */
    protected LlmVerdict parseLlmResponse(String content) {
        if (!StringUtils.hasText(content)) {
            return LlmVerdict.degraded();
        }
        String taskToken = extractTaskToken(content);
        if ("NONE".equalsIgnoreCase(taskToken)) {
            return LlmVerdict.none();
        }
        for (AgentTaskType type : AgentTaskType.values()) {
            if (type.name().equalsIgnoreCase(taskToken)) {
                return LlmVerdict.task(type);
            }
        }
        return LlmVerdict.degraded();
    }

    private String extractTaskToken(String raw) {
        String text = raw.trim();
        if (text.startsWith("```")) {
            int newline = text.indexOf('\n');
            if (newline >= 0) {
                text = text.substring(newline + 1);
            }
            int lastFence = text.lastIndexOf("```");
            if (lastFence >= 0) {
                text = text.substring(0, lastFence);
            }
            text = text.trim();
        }

        Matcher matcher = TASK_TOKEN.matcher(text);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        // 无 task 字段时，把整个输出当作裸枚举名/NONE
        return text.replaceAll("[^A-Za-z_]", "");
    }

    private String buildSystemPrompt() {
        return """
                你是微信聊天机器人的意图路由器。请判断用户最新消息是否属于以下 Agent 任务之一，只输出一个 JSON：
                {"task": "GITHUB_REPO_DIGEST"}        用户想速读/分析某个 GitHub 仓库（提供了仓库链接或 owner/repo），或想了解仓库是做什么的
                {"task": "TECH_ROADMAP"}              用户想为某个技术主题规划学习路线、入门路线、怎么学（注意：单独出现"学习"不一定就是路线规划）
                {"task": "DOC_REVIEW"}                用户想对技术文档/博客草稿做规范审稿、排版检查，或直接粘贴了长篇文章/代码等待处理
                {"task": "DAILY_DIGEST_SUBSCRIBE"}    用户想订阅或设置每日/每周技术晨报/日报的定时推送
                {"task": "NONE"}                      其它情况：普通聊天、普通问答、意图不明确

                要求：
                - 高度确信才选择任务，拿不准时一律 NONE，宁可少路由也不要误路由；
                - 不要输出任何解释或多余文本，只输出上述 JSON。
                """;
    }

    /**
     * 关键词规则兜底（旧行为）。自上而下顺序匹配，先命中优先。
     */
    private Optional<AgentTaskType> ruleRoute(String text) {
        // 优先级1：Github 仓库识别（链接 + 关键词）
        if (containsGitHubReference(text)) {
            return Optional.of(AgentTaskType.GITHUB_REPO_DIGEST);
        }
        // 优先级2：学习路线
        if (containsAny(text, ROADMAP_HINTS)) {
            return Optional.of(AgentTaskType.TECH_ROADMAP);
        }
        // 优先级3：文档审稿
        if (containsAny(text, REVIEW_HINTS)) {
            return Optional.of(AgentTaskType.DOC_REVIEW);
        }
        // 优先级4：晨报订阅
        if (containsAny(text, DIGEST_HINTS)) {
            return Optional.of(AgentTaskType.DAILY_DIGEST_SUBSCRIBE);
        }
        // 优先级5：长文本文章兜底 —— 用户直接贴了文章/代码/草稿，先确认是否需要审稿
        if (AgentTextUtils.extractDraftText(text).isPresent()) {
            return Optional.of(AgentTaskType.DOC_REVIEW);
        }
        return Optional.empty();
    }

    private boolean hasGithubUrl(String text) {
        return text.toLowerCase().contains("github.com/");
    }

    private boolean containsGitHubReference(String text) {
        String normalized = text.toLowerCase();
        boolean hasGithubUrl = normalized.contains("github.com/");
        boolean hasKeyword = containsAny(normalized, GITHUB_HINTS);
        return hasGithubUrl || hasKeyword;
    }

    private boolean containsAny(String text, List<String> candidates) {
        return candidates.stream().anyMatch(text::contains);
    }

    /**
     * LLM 语义路由的判定结果。
     * TASK：命中某个 Agent 任务；NONE：明确不属于任何任务；DEGRADED：LLM 不可用/无法解析。
     */
    public record LlmVerdict(LlmDecision decision, AgentTaskType task) {

        public static LlmVerdict task(AgentTaskType task) {
            return new LlmVerdict(LlmDecision.TASK, task);
        }

        public static LlmVerdict none() {
            return new LlmVerdict(LlmDecision.NONE, null);
        }

        public static LlmVerdict degraded() {
            return new LlmVerdict(LlmDecision.DEGRADED, null);
        }

        public enum LlmDecision {
            TASK,
            NONE,
            DEGRADED
        }
    }
}
