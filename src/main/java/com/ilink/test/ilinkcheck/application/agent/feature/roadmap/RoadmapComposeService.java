package com.ilink.test.ilinkcheck.application.agent.feature.roadmap;

import com.ilink.test.ilinkcheck.application.agent.dto.roadmap.DevToArticle;
import com.ilink.test.ilinkcheck.application.agent.dto.roadmap.RoadmapPlan;
import com.ilink.test.ilinkcheck.application.agent.dto.roadmap.RoadmapStep;
import com.ilink.test.ilinkcheck.application.agent.dto.roadmap.SearchResult;
import com.ilink.test.ilinkcheck.application.tool.categories.translation.TranslationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ilink.test.ilinkcheck.infrastructure.common.TimeoutExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;

/**
 * 技术路线业务逻辑层（人4 核心交付之一）。
 *
 * <p>职责：把"主题 + Dev.to 文章 + WebSearch 补充参考"整理成结构化的学习路线。
 * 编排采用「LLM CoT 优先 + 模板兜底」双路径：</p>
 * <ul>
 *   <li>有 {@code routingChatClient} 时，{@link #compose} 调用 LLM 基于真实资料做链式推理
 *       （预备知识 → 核心概念 → 实战建议），每步生成贴合主题、点名资源的动态说明；</li>
 *   <li>无 ChatClient / LLM 调用失败 / 解析异常时，自动退回 {@link #composeTemplate} 的写死模板，
 *       保证路线在任何情况下都能产出；</li>
 *   <li>{@link #render} 与调用方（PhaseExecutor、Skill、Tool）完全不受影响——智能与模板产出的是同一个 {@link RoadmapPlan}。</li>
 * </ul>
 *
 * <p>设计要点：
 * <ul>
 *   <li>ChatClient 用 {@code required = false} 可选注入，沿用 {@code routingChatClient}
 *       （无记忆、确定性高的规划用客户端），不污染对话上下文；</li>
 *   <li>每条资料的来源（如 GitHub / Dev.to）由链接域名在 {@link #deriveSource} 中自动推断，
 *       并以"（来源：X）"形式标注在标题旁；</li>
 *   <li>{@link #buildReply} 是给 Skill / Tool 用的"一站式"便捷方法。</li>
 * </ul>
 * </p>
 */
@Slf4j
@Service
public class RoadmapComposeService {

    /**
     * 规划用 ChatClient（可选）。来自 {@code routingChatClient} Bean（无记忆、确定性高）。
     * 未注入时（如离线测试、未配置 DashScope key）{@link #compose} 直接走模板兜底。
     */
    private ChatClient plannerClient;

    /**
     * 翻译服务（可选）。未注入时（如离线测试、未配置翻译 API）翻译步骤自动跳过，资料保持原文。
     * 真实实现为 {@code QwenTranslationService}（对接 DashScope），由 Spring 自动装配；
     * 这里用 {@code required = false} 保证无翻译能力时路线功能仍可正常运行。
     */
    private TranslationService translationService;

    /** 推荐资料最大显示条数（超出部分截断，减少回复长度与翻译耗时） */
    private static final int MAX_ARTICLES = 3;

    /** 补充参考最大显示条数（超出部分截断） */
    private static final int MAX_REFERENCES = 3;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** 路线缓存有效期：30 分钟。演示中同一主题二次提问、或后续「翻译成中文」都能直接命中。 */
    private static final long PLAN_CACHE_TTL_MS = 30 * 60 * 1000L;

    /** 缓存条目上限，超过后整体清空（演示/单机场景足够，避免引入额外依赖）。 */
    private static final int PLAN_CACHE_MAX_SIZE = 64;

    /**
     * 主题 → 已编排路线 的进程内缓存。
     * 关键收益：用户先问「我想学 RAG」再发「翻译成中文」时，第二次不必重新调用 LLM 编排，
     * 直接复用上次的 plan，只做翻译，响应时间减半以上。
     */
    private final Map<String, CachedPlan> planCache = new ConcurrentHashMap<>();

    /** 翻译最长等待时间（毫秒）。超时直接用英文原文回复，保证演示不卡死。 */
    private long translateTimeoutMs = 20000;

    @org.springframework.beans.factory.annotation.Value("${app.agent.translate-timeout-ms:20000}")
    public void setTranslateTimeoutMs(long translateTimeoutMs) {
        this.translateTimeoutMs = translateTimeoutMs;
    }

    @Autowired(required = false)
    public void setTranslationService(TranslationService translationService) {
        this.translationService = translationService;
    }

    /**
     * 注入规划用 ChatClient。优先使用 {@code plannerChatClient}（快模型、低温、无记忆），
     * 它比通用对话模型快数倍，是路线响应速度的关键。
     */
    @Autowired(required = false)
    public void setPlannerClient(@Qualifier("plannerChatClient") ChatClient plannerChatClient) {
        this.plannerClient = plannerChatClient;
    }

    /** 缓存条目：已编排的路线 + 写入时间戳。 */
    private record CachedPlan(RoadmapPlan plan, long timestampMs) {
        boolean isFresh() {
            return System.currentTimeMillis() - timestampMs < PLAN_CACHE_TTL_MS;
        }
    }

    /**
     * 已知站点域名 → 友好来源名。新增站点只需在此补充，无需改动渲染逻辑。
     */
    private static final Map<String, String> KNOWN_SOURCES = Map.ofEntries(
            Map.entry("github.com", "GitHub"),
            Map.entry("gist.github.com", "GitHub Gist"),
            Map.entry("gitlab.com", "GitLab"),
            Map.entry("gitee.com", "Gitee"),
            Map.entry("dev.to", "Dev.to"),
            Map.entry("stackoverflow.com", "Stack Overflow"),
            Map.entry("medium.com", "Medium"),
            Map.entry("youtube.com", "YouTube"),
            Map.entry("www.youtube.com", "YouTube"),
            Map.entry("bilibili.com", "哔哩哔哩"),
            Map.entry("zhihu.com", "知乎"),
            Map.entry("juejin.cn", "掘金"),
            Map.entry("csdn.net", "CSDN"),
            Map.entry("spring.io", "Spring 官方文档"),
            Map.entry("cloud.spring.io", "Spring 官方文档"),
            Map.entry("docs.spring.io", "Spring 官方文档"),
            Map.entry("docs.oracle.com", "Oracle 官方文档"),
            Map.entry("kubernetes.io", "Kubernetes 官方文档"),
            Map.entry("docs.python.org", "Python 官方文档"),
            Map.entry("python.org", "Python 官方文档"),
            Map.entry("react.dev", "React 官方文档"),
            Map.entry("developer.mozilla.org", "MDN Web Docs")
    );

    /**
     * 常见前缀子域，推断来源名时忽略（如 docs.spring.io → Spring）。
     */
    private static final Set<String> COMMON_PREFIX_SUBDOMAINS = Set.of(
            "www", "docs", "doc", "blog", "api", "guide", "learn", "developer", "cn", "en"
    );

    /**
     * LLM CoT 编排用的系统提示词：要求模型按"预备知识 → 核心概念 → 实战建议"三阶段
     * 链式推理，每步给出标题 + 贴合主题（最好点名资料）的说明，并严格输出 JSON。
     */
    private static final String COT_PROMPT = """
            你是一位资深技术学习路径设计师。请为给定主题设计一份循序渐进的学习路线，严格分为三个阶段：
            1. prerequisites（预备知识）：学习该主题前必须掌握的底层基础；
            2. coreConcepts（核心概念）：该主题本身最关键的原理与设计思想；
            3. projectIdeas（实战建议）：用最小成本动手验证理解的实践项目。

            要求：
            - 每个阶段给出 2~4 个步骤，每个步骤包含 title（简短标题）与 description（1~2 句说明）；
            - 说明必须具体、与主题强相关，最好点名上下文中提供的资料；禁止使用"XX 的核心原理"这类空泛模板；
            - 只输出如下结构的 JSON，不要任何额外解释、不要 markdown 代码块：
            {"prerequisites":[{"title":"","description":""}],"coreConcepts":[{"title":"","description":""}],"projectIdeas":[{"title":"","description":""}]}
            """;

    /**
     * 产出结构化学习路线。
     *
     * <p>编排策略：若已注入规划用 ChatClient，则优先走 LLM CoT 路径
     * （{@link #composeWithLlm}），基于真实资料生成贴合主题的动态说明；
     * 任何失败（无 Client / 调用异常 / JSON 解析失败 / 结构为空）都安全退回
     * {@link #composeTemplate} 的写死模板，保证路线始终可用。</p>
     */
    public RoadmapPlan compose(String topic, List<DevToArticle> articles, List<SearchResult> references) {
        // 缓存命中：同主题 30 分钟内直接复用，省掉一次 LLM 往返（后续「翻译成中文」的主要提速点）。
        // 例外：缓存的 plan 没有推荐文章、而本次抓到了文章 → 说明上次抓取失败被缓存，跳过缓存重新编排。
        String cacheKey = cacheKeyOf(topic);
        CachedPlan cached = planCache.get(cacheKey);
        boolean cachedMissingArticles = cached != null
                && (cached.plan().recommendedArticles() == null || cached.plan().recommendedArticles().isEmpty())
                && articles != null && !articles.isEmpty();
        if (cached != null && cached.isFresh() && !cachedMissingArticles) {
            log.info("[路线编排] 命中缓存，跳过 LLM 编排：{}", topic);
            return cached.plan();
        }

        long startMs = System.currentTimeMillis();
        RoadmapPlan plan;
        if (plannerClient != null) {
            try {
                plan = composeWithLlm(topic, articles, references);
                log.info("[路线编排] LLM 编排完成，耗时 {} ms", System.currentTimeMillis() - startMs);
            } catch (Exception e) {
                log.warn("LLM 路线编排失败，退回模板兜底：{}", e.getMessage());
                plan = composeTemplate(topic, articles);
            }
        } else {
            plan = composeTemplate(topic, articles);
        }

        cachePlan(cacheKey, plan);
        return plan;
    }

    /**
     * 一次性完成「编排 + 翻译 + 渲染」，其中翻译与 LLM 编排<b>并发</b>执行。
     *
     * <p>翻译的输入只有抓取到的资料，与 plan 无关，因此没有必要等编排结束再翻译。
     * 并发后总耗时从「编排 + 翻译」变为「max(编排, 翻译)」，翻译场景下基本省掉一半时间。
     * 翻译超时或失败一律回退英文原文，不影响路线正常返回。</p>
     */
    public String composeAndRender(String topic,
                                   List<DevToArticle> articles,
                                   List<SearchResult> references,
                                   boolean translate) {
        List<SearchResult> truncatedRefs = truncate(references, MAX_REFERENCES);
        List<DevToArticle> truncatedArticles = truncate(articles, MAX_ARTICLES);

        // 先把翻译任务丢到后台，紧接着在当前线程做 LLM 编排，两者重叠执行
        Future<Map<String, String>> translationFuture =
                (translate && translationService != null)
                        ? TimeoutExecutor.submit(() -> batchTranslateAll(truncatedArticles, truncatedRefs))
                        : CompletableFuture.completedFuture(Map.of());

        if (translate && translationService == null) {
            log.warn("[路线渲染] translate=true 但 TranslationService 未注入，跳过翻译（检查 QwenTranslationService 是否被 Spring 扫描）");
        }

        RoadmapPlan plan = compose(topic, articles, references);

        // 超时/失败统一走 TimeoutExecutor 兜底（取消任务并回退英文原文）
        Map<String, String> translationMap = TimeoutExecutor.await(
                translationFuture, translateTimeoutMs, Map.of(), "路线翻译");

        return render(plan, truncatedRefs, translationMap);
    }

    /** 缓存键：主题去空格转小写，保证「RAG」「rag」「 Rag 」命中同一条。 */
    private static String cacheKeyOf(String topic) {
        return topic == null ? "" : topic.trim().toLowerCase();
    }

    private void cachePlan(String cacheKey, RoadmapPlan plan) {
        if (planCache.size() >= PLAN_CACHE_MAX_SIZE) {
            planCache.clear();
        }
        planCache.put(cacheKey, new CachedPlan(plan, System.currentTimeMillis()));
    }

    /**
     * LLM CoT 编排路径：用 routingChatClient 基于主题 + 真实资料链式推理出三阶段步骤，
     * 再把结构化 JSON 映射为 {@link RoadmapPlan}。供单元测试用子类覆写 {@link #generatePlanDto}。
     */
    RoadmapPlan composeWithLlm(String topic, List<DevToArticle> articles, List<SearchResult> references) {
        String context = buildContext(topic, articles, references);
        RoadmapPlanDto dto = generatePlanDto(topic, context);
        return toPlan(topic, dto, articles);
    }

    /**
     * 调用规划用 ChatClient 生成结构化路线 JSON。
     * 拆为独立 protected 方法，便于测试时用子类覆写（无需真实 LLM）。
     */
    protected RoadmapPlanDto generatePlanDto(String topic, String context) {
        String raw = plannerClient.prompt()
                .system(COT_PROMPT)
                .user(context)
                .call()
                .content();
        return parsePlanDto(raw);
    }

    /**
     * 把 LLM 原文解析为 {@link RoadmapPlanDto}，兼容 ```json 代码围栏与首尾多余文本。
     */
    RoadmapPlanDto parsePlanDto(String raw) {
        String json = extractJson(raw);
        try {
            return OBJECT_MAPPER.readValue(json, RoadmapPlanDto.class);
        } catch (Exception e) {
            throw new IllegalStateException("无法解析路线 JSON：" + e.getMessage(), e);
        }
    }

    /**
     * 将 {@link RoadmapPlanDto} 映射为 {@link RoadmapPlan}，并对空结构做校验。
     */
    private RoadmapPlan toPlan(String topic, RoadmapPlanDto dto, List<DevToArticle> articles) {
        if (dto == null) {
            throw new IllegalStateException("LLM 返回空计划");
        }
        List<RoadmapStep> prerequisites = mapSteps(dto.prerequisites);
        List<RoadmapStep> coreConcepts = mapSteps(dto.coreConcepts);
        List<RoadmapStep> projectIdeas = mapSteps(dto.projectIdeas);
        if (prerequisites.isEmpty() && coreConcepts.isEmpty() && projectIdeas.isEmpty()) {
            throw new IllegalStateException("LLM 未返回任何步骤");
        }
        return new RoadmapPlan(
                topic,
                prerequisites,
                coreConcepts,
                projectIdeas,
                truncate(articles, MAX_ARTICLES)
        );
    }

    private List<RoadmapStep> mapSteps(List<RoadmapPlanDto.StepDto> steps) {
        if (steps == null || steps.isEmpty()) {
            return List.of();
        }
        return steps.stream()
                .filter(s -> s != null && s.title != null && !s.title.isBlank())
                .map(s -> RoadmapStep.of(s.title, defaultText(s.description, "")))
                .toList();
    }

    /**
     * 构造喂给 LLM 的上下文：主题 + 真实可点名资料（文章 / 参考）。
     * 资料缺失时明确标注，避免 LLM 凭空编造。
     */
    String buildContext(String topic, List<DevToArticle> articles, List<SearchResult> references) {
        StringBuilder sb = new StringBuilder();
        sb.append("主题：").append(topic).append("\n\n");
        sb.append("可用资料（用于生成贴合资源的说明，可在说明中点名这些资料）：\n");
        boolean hasAny = false;
        if (articles != null) {
            for (DevToArticle a : articles) {
                sb.append("- [文章] ").append(a.title()).append(" | ").append(a.url()).append("\n");
                hasAny = true;
            }
        }
        if (references != null) {
            for (SearchResult r : references) {
                sb.append("- [参考] ").append(r.title()).append(" | ").append(r.url()).append("\n");
                hasAny = true;
            }
        }
        if (!hasAny) {
            sb.append("- （暂无可用资料，请基于通用技术常识设计）\n");
        }
        return sb.toString();
    }

    /**
     * 写死的模板路线（兜底用）。不依赖任何外部服务，保证路线永远能产出。
     */
    private RoadmapPlan composeTemplate(String topic, List<DevToArticle> articles) {
        List<RoadmapStep> prerequisites = List.of(
                RoadmapStep.of("基础编程语法", "掌握变量、循环、条件判断、函数定义等核心语法"),
                RoadmapStep.of("命令行与调试能力", "熟悉常用 shell 命令与 IDE 断点调试技巧"),
                RoadmapStep.of("阅读英文文档的基本能力", "能独立阅读官方文档与技术社区讨论")
        );
        List<RoadmapStep> coreConcepts = List.of(
                RoadmapStep.of(topic + " 的核心概念", topic + " 的核心原理与设计思想"),
                RoadmapStep.of(topic + " 的运行机制", topic + " 在实际环境中的执行流程"),
                RoadmapStep.of(topic + " 的常见约束", topic + " 使用时的限制与注意事项")
        );
        List<RoadmapStep> projectIdeas = List.of(
                RoadmapStep.of("做一个最小 demo", "跑通官方 Hello World 或最小可用示例"),
                RoadmapStep.of("完成一个可复现实践", "基于真实场景完成一个小功能并记录步骤"),
                RoadmapStep.of("整理一篇学习笔记", "输出学习总结，可分享给团队或社区")
        );
        return new RoadmapPlan(
                topic,
                prerequisites,
                coreConcepts,
                projectIdeas,
                truncate(articles, MAX_ARTICLES)
        );
    }

    /**
     * 将 {@link RoadmapPlan} 渲染为 Markdown 格式的学习路线回复。
     *
     * <p>每个阶段下的步骤会渲染为"标题：说明"格式。如果某步骤的 description 为空，
     * 则只显示标题（向后兼容旧数据）。</p>
     */
    public String render(RoadmapPlan plan, List<SearchResult> references) {
        return render(plan, references, false);
    }

    /**
     * 将 {@link RoadmapPlan} 渲染为 Markdown 格式的学习路线回复。
     *
     * <p>当 {@code translate} 为 true 时，推荐资料与补充参考中的英文标题/摘要会被
     * 翻译成中文（通过 {@link TranslationService} 批量翻译，一次 API 调用处理所有文本，
     * 未配置时自动跳过、保留原文）。</p>
     *
     * <p>每个阶段下的步骤会渲染为"标题：说明"格式。如果某步骤的 description 为空，
     * 则只显示标题（向后兼容旧数据）。</p>
     */
    public String render(RoadmapPlan plan, List<SearchResult> references, boolean translate) {
        // 截断：只取前 N 条，减少回复长度与翻译 API 耗时
        List<SearchResult> truncatedRefs = truncate(references, MAX_REFERENCES);

        // 批量翻译：收集所有需要翻译的文本 → 一次 API 调用 → 按顺序使用结果
        if (translate && translationService == null) {
            log.warn("[路线渲染] translate=true 但 TranslationService 未注入，跳过翻译（检查 QwenTranslationService 是否被 Spring 扫描）");
        }
        Map<String, String> translationMap = (translate && translationService != null)
                ? batchTranslateAll(plan.recommendedArticles(), truncatedRefs)
                : Map.of();

        // 诊断：翻译请求了但映射为空 → API 调用失败，记录日志
        if (translate && translationService != null && translationMap.isEmpty()) {
            log.warn("[路线渲染] ⚠️ 翻译请求已发出但返回空映射！检查上方 [翻译] 日志确认 API 是否报错");
        }

        return render(plan, truncatedRefs, translationMap);
    }

    /**
     * 用已经准备好的「原文 → 译文」映射渲染回复。翻译已在别处（可能是并发任务）完成，
     * 这里只做纯字符串拼装，不再触发任何网络调用。映射为空即全部使用原文。
     */
    public String render(RoadmapPlan plan, List<SearchResult> references, Map<String, String> translationMap) {
        List<DevToArticle> articles = plan.recommendedArticles();
        List<SearchResult> truncatedRefs = truncate(references, MAX_REFERENCES);
        Map<String, String> safeMap = translationMap == null ? Map.of() : translationMap;

        String articleText = (articles == null || articles.isEmpty())
                ? "- 暂无 Dev.to 推荐文章"
                : articles.stream()
                .map(a -> formatArticleLine(a, safeMap))
                .reduce((a, b) -> a + System.lineSeparator() + b)
                .orElse("- 暂无 Dev.to 推荐文章");

        String referenceText = (truncatedRefs == null || truncatedRefs.isEmpty())
                ? "- 暂无补充检索结果"
                : truncatedRefs.stream()
                .map(r -> formatReferenceLine(r, safeMap))
                .reduce((a, b) -> a + System.lineSeparator() + b)
                .orElse("- 暂无补充检索结果");

        return """
                %s 学习路线

                第一阶段：预备知识
                %s

                第二阶段：核心概念
                %s

                第三阶段：实战建议
                %s

                推荐资料
                %s

                补充参考
                %s
                """.formatted(
                plan.topic(),
                renderSteps(plan.prerequisites()),
                renderSteps(plan.coreConcepts()),
                renderSteps(plan.projectIdeas()),
                articleText,
                referenceText
        ).trim();
    }

    /**
     * 一站式便捷方法：先 compose 再 render，给 Skill / Tool 这类"自包含"调用方使用。
     */
    public String buildReply(String topic, List<DevToArticle> articles, List<SearchResult> references) {
        return render(compose(topic, articles, references), references);
    }

    // ── 内部方法 ──

    /**
     * 将单篇推荐文章渲染为带可点击链接与来源标注的 Markdown 行。
     * 链接格式：[标题](url)。来源（如 GitHub / Dev.to）由链接域名自动推断，
     * 显示在标题旁，格式为"（来源：X）"。若文章 url 为空则退化为纯标题。
     */
    private String formatArticleLine(DevToArticle article, Map<String, String> translationMap) {
        String title = getTranslated(article.title(), translationMap);
        String description = getTranslated(article.description(), translationMap);
        String titlePart = isLinkable(article.url())
                ? "[" + title + "](" + article.url() + ")"
                : title;
        return "- " + titlePart
                + "（来源：" + deriveSource(article.url()) + "）"
                + "：" + defaultText(description, "暂无摘要")
                + "（👍 " + article.positiveReactions() + "）";
    }

    /**
     * 将单条补充参考渲染为带可点击链接与来源标注的 Markdown 行。
     * 链接格式：[标题](url)。来源由链接域名自动推断，显示在标题旁。
     */
    private String formatReferenceLine(SearchResult result, Map<String, String> translationMap) {
        String title = getTranslated(result.title(), translationMap);
        String snippet = getTranslated(result.snippet(), translationMap);
        String titlePart = isLinkable(result.url())
                ? "[" + title + "](" + result.url() + ")"
                : title;
        return "- " + titlePart
                + "（来源：" + deriveSource(result.url()) + "）"
                + "：" + defaultText(snippet, "暂无摘要");
    }

    /**
     * 从翻译映射表中查找已翻译文本；找不到或翻译结果为空则返回原文。
     */
    private static String getTranslated(String original, Map<String, String> translationMap) {
        if (original == null || original.isBlank()) return original;
        String translated = translationMap.get(original);
        return (translated != null && !translated.isBlank()) ? translated.trim() : original;
    }

    /**
     * 收集所有需要翻译的文本，批量调用一次 TranslationService，返回 原文→译文 映射。
     * 翻译服务不可用或调用失败时返回空映射（各处会自动使用原文）。
     */
    private Map<String, String> batchTranslateAll(List<DevToArticle> articles, List<SearchResult> references) {
        if (translationService == null) {
            return Map.of();
        }
        try {
            List<String> textsToTranslate = new ArrayList<>();
            // 用一个并行列表记录原始文本，用于构建映射
            Map<Integer, String> indexToOriginal = new HashMap<>();
            int idx = 0;

            if (articles != null) {
                for (DevToArticle a : articles) {
                    if (a.title() != null && !a.title().isBlank()) { textsToTranslate.add(a.title()); indexToOriginal.put(idx++, a.title()); }
                    if (a.description() != null && !a.description().isBlank()) { textsToTranslate.add(a.description()); indexToOriginal.put(idx++, a.description()); }
                }
            }
            if (references != null) {
                for (SearchResult r : references) {
                    if (r.title() != null && !r.title().isBlank()) { textsToTranslate.add(r.title()); indexToOriginal.put(idx++, r.title()); }
                    if (r.snippet() != null && !r.snippet().isBlank()) { textsToTranslate.add(r.snippet()); indexToOriginal.put(idx++, r.snippet()); }
                }
            }

            if (textsToTranslate.isEmpty()) {
                return Map.of();
            }

            log.info("[路线渲染] 开始批量翻译 {} 段文本", textsToTranslate.size());
            List<String> translated = translationService.batchTranslate(textsToTranslate, "English", "Chinese");

            // 构建原文 → 译文 映射
            Map<String, String> result = new HashMap<>();
            for (int i = 0; i < translated.size(); i++) {
                String original = indexToOriginal.get(i);
                String trans = translated.get(i);
                if (original != null && trans != null && !trans.isBlank()) {
                    result.put(original, trans.trim());
                }
            }
            log.info("[路线渲染] 批量翻译完成，共 {} 条映射", result.size());
            return result;
        } catch (Exception e) {
            log.error("[路线渲染] 批量翻译失败，使用原文", e);
            return Map.of();
        }
    }

    /**
     * 安全截断列表至指定最大长度。null 返回空列表，不超过上限则原样返回。
     */
    private static <T> List<T> truncate(List<T> list, int max) {
        if (list == null) return List.of();
        return list.size() <= max ? list : list.subList(0, max);
    }

    /**
     * 根据链接域名推断资料的来源说明。
     *
     * <p>优先匹配已知站点（GitHub / Dev.to / Stack Overflow 等）给出友好的中文/品牌名；
     * 未知域名则回退为"去掉常见前缀子域 + 首字母大写"的可读名称（如 example.com → Example）。
     * 链接为空或无法解析时返回"未知来源"。</p>
     */
    String deriveSource(String url) {
        if (url == null || url.isBlank()) {
            return "未知来源";
        }
        try {
            String host = URI.create(url).getHost();
            if (host == null || host.isBlank()) {
                return "未知来源";
            }
            host = host.toLowerCase();
            if (host.startsWith("www.")) {
                host = host.substring(4);
            }
            if (KNOWN_SOURCES.containsKey(host)) {
                return KNOWN_SOURCES.get(host);
            }
            return capitalize(deriveCoreLabel(host));
        } catch (Exception ignored) {
            return "未知来源";
        }
    }

    /**
     * 从域名中提取可读的核心名称：去掉 docs / blog / api 等常见前缀子域，取有意义的主体段。
     */
    private String deriveCoreLabel(String host) {
        String[] parts = host.split("\\.");
        int start = 0;
        if (parts.length > 2 && COMMON_PREFIX_SUBDOMAINS.contains(parts[0])) {
            start = 1;
        }
        return parts[start];
    }

    private String capitalize(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    /**
     * 判断 url 是否可渲染为链接（非空且符合 http/https 协议）。
     */
    private boolean isLinkable(String url) {
        return url != null && !url.isBlank()
                && (url.startsWith("http://") || url.startsWith("https://"));
    }

    /**
     * 将步骤列表渲染为 Markdown bullet 列表。
     * 有 description 时输出 "- 标题：说明"，无 description 时只输出 "- 标题"。
     */
    private String renderSteps(List<RoadmapStep> steps) {
        if (steps == null || steps.isEmpty()) {
            return "- （暂无）";
        }
        return steps.stream()
                .map(step -> {
                    if (step.description() == null || step.description().isBlank()) {
                        return "- " + step.title();
                    }
                    return "- " + step.title() + "：" + step.description();
                })
                .reduce((a, b) -> a + System.lineSeparator() + b)
                .orElse("- （暂无）");
    }

    private String defaultText(String text, String fallback) {
        return text == null || text.isBlank() ? fallback : text.trim();
    }

    /**
     * 从 LLM 原文中提取纯 JSON：去除 ```json ... ``` 围栏与首尾空白。
     */
    private static String extractJson(String raw) {
        if (raw == null) {
            return "{}";
        }
        String text = raw.trim();
        if (text.startsWith("```")) {
            int firstNewline = text.indexOf('\n');
            if (firstNewline >= 0) {
                text = text.substring(firstNewline + 1);
            }
            int lastFence = text.lastIndexOf("```");
            if (lastFence >= 0) {
                text = text.substring(0, lastFence);
            }
            text = text.trim();
        }
        return text;
    }

    /**
     * LLM 返回路线的结构化中间对象（Jackson 直接映射）。
     * 仅用于编排层内部，与对外 {@link RoadmapPlan} 解耦。
     */
    public static class RoadmapPlanDto {
        public List<StepDto> prerequisites;
        public List<StepDto> coreConcepts;
        public List<StepDto> projectIdeas;

        public static class StepDto {
            public String title;
            public String description;

            public StepDto() {
            }

            public StepDto(String title, String description) {
                this.title = title;
                this.description = description;
            }
        }
    }
}
