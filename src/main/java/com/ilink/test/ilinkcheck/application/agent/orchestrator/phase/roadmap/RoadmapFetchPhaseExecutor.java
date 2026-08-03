package com.ilink.test.ilinkcheck.application.agent.orchestrator.phase.roadmap;

import com.ilink.test.ilinkcheck.application.agent.dto.roadmap.DevToArticle;
import com.ilink.test.ilinkcheck.application.agent.dto.roadmap.SearchResult;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.model.*;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.support.AgentTextUtils;
import com.ilink.test.ilinkcheck.application.agent.spi.mcp.DevToArticleProvider;
import com.ilink.test.ilinkcheck.application.agent.spi.mcp.WebSearchProvider;
import com.ilink.test.ilinkcheck.infrastructure.common.TimeoutExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

/**
 * 学习路线第一阶段。
 * 先提取学习主题，再拉取 Dev.to 文章和 WebSearch 参考资料。
 *
 * <p>性能：两个外部数据源互不依赖，通过 {@link TimeoutExecutor} 并发拉取，
 * 总耗时从「两者之和」降为「两者较慢的那个」；并对单个来源设置超时，
 * 任一来源卡住时按空结果继续，避免整条链路被拖死。</p>
 */
@Slf4j
@Service
public class RoadmapFetchPhaseExecutor implements PhaseExecutor {

    private final DevToArticleProvider devToArticleProvider;
    private final WebSearchProvider webSearchProvider;

    /** 单个数据源的最长等待时间（毫秒），超时按空结果处理。 */
    @Value("${app.agent.fetch-timeout-ms:15000}")
    private long fetchTimeoutMs = 15000;

    public RoadmapFetchPhaseExecutor(DevToArticleProvider devToArticleProvider,
                                     WebSearchProvider webSearchProvider) {
        this.devToArticleProvider = devToArticleProvider;
        this.webSearchProvider = webSearchProvider;
    }

    @Override
    public boolean supports(AgentTaskType taskType, AgentPhase phase) {
        return taskType == AgentTaskType.TECH_ROADMAP && phase == AgentPhase.ROADMAP_FETCH;
    }

    @Override
    public PhaseExecutionResult execute(AgentState state) {
        String topic = AgentTextUtils.extractTopic(state.getLatestUserMessage())
                .or(() -> AgentTextUtils.extractTopic(state.getOriginalUserMessage()))
                .orElse(null);

        if (topic == null) {
            return PhaseExecutionResult.waitForUser(
                    AgentPhase.ROADMAP_FETCH,
                    "你想学什么技术？直接回复一个主题，比如 `eBPF`、`RAG` 或 `Spring AI`。",
                    Map.of()
            );
        }

        // 是否要求把资料翻译成中文（如"我想学 RAG 翻译成中"）
        boolean translate = AgentTextUtils.wantsChinese(state.getLatestUserMessage())
                || AgentTextUtils.wantsChinese(state.getOriginalUserMessage());

        // 注意：保留主题原始大小写（如 eBPF），大小写归一化交给 Provider 实现决定（Dev.to 标签本身大小写不敏感）。
        // 两个来源先提交到后台线程池再等待：总耗时 = max(A, B) 而不是 A + B。
        long startMs = System.currentTimeMillis();
        Future<List<DevToArticle>> articleFuture = TimeoutExecutor.submit(
                () -> devToArticleProvider.fetchByTag(topic, 3));
        Future<List<SearchResult>> referenceFuture = TimeoutExecutor.submit(
                () -> webSearchProvider.search(topic + " tutorial", 2));

        List<DevToArticle> articles = TimeoutExecutor.await(articleFuture, fetchTimeoutMs, List.of(), "Dev.to 文章");
        List<SearchResult> references = TimeoutExecutor.await(referenceFuture, fetchTimeoutMs, List.of(), "补充检索");
        log.info("[路线抓取] 主题={} 并发抓取完成，文章 {} 篇 / 参考 {} 条，耗时 {} ms",
                topic, articles.size(), references.size(), System.currentTimeMillis() - startMs);

        // 降级 1：两个外部源都失败（通常因为网络不通）→ 内置演示数据
        if (articles.isEmpty() && references.isEmpty()) {
            log.warn("[路线抓取] ⚠️ 两个数据源均为空，启用演示模式降级");
            articles = demoArticles(topic);
            references = demoReferences(topic);
        }
        // 降级 2：Dev.to 无结果但搜索有数据 → 从搜索结果中取前 N 条包装为推荐文章，
        //         并从补充参考中移除已提升的条目，避免两个区域显示完全相同的内容
        else if (articles.isEmpty() && !references.isEmpty()) {
            int take = Math.min(3, references.size());
            log.info("[路线抓取] Dev.to 无结果，从搜索结果补充 {} 条作为推荐文章（剩余 {} 条留在补充参考）",
                    take, references.size() - take);
            articles = promoteFromReferences(references, take);
            references = references.subList(take, references.size()); // 去重：提升过的不再重复展示
        }

        // 若用户一开始就要求中文版（如"我想学 RAG 翻译成中文"），跳过"先精简再确认"，
        // 直接进入确认阶段由它渲染带翻译的完整版；否则进入精简版 + 询问流程。
        AgentPhase nextPhase = translate ? AgentPhase.ROADMAP_CONFIRM_DETAIL : AgentPhase.ROADMAP_COMPOSE;
        return PhaseExecutionResult.next(nextPhase, Map.of(
                AgentArtifacts.REQUEST_TOPIC, topic,
                AgentArtifacts.ROADMAP_ARTICLES, articles,
                AgentArtifacts.ROADMAP_REFERENCES, references,
                AgentArtifacts.ROADMAP_TRANSLATE, translate
        ));
    }

    /**
     * 从搜索结果中取前 N 条包装为 DevToArticle 格式，填充推荐文章区域。
     * 当 Dev.to 对冷门标签（如 rag、ebpf）无匹配结果时自动启用。
     */
    private static List<DevToArticle> promoteFromReferences(List<SearchResult> references, int max) {
        return references.stream()
                .limit(max)
                .map(r -> new DevToArticle(
                        r.title(),
                        r.url(),
                        "",
                        0,
                        r.snippet()
                ))
                .toList();
    }

    // ── 演示模式降级数据（网络不通时自动启用）─────────────────────────────

    private static List<DevToArticle> demoArticles(String topic) {
        return List.of(
                new DevToArticle(
                        "Getting Started with " + topic,
                        "https://dev.to/example/getting-started",
                        topic + ",tutorial",
                        320,
                        "从基础概念、运行机制和第一个 demo 入手的入门文章。"
                ),
                new DevToArticle(
                        "Practical " + topic + " Patterns",
                        "https://dev.to/example/practical-patterns",
                        topic + ",practice",
                        240,
                        "更偏实战，关注常见模式、调试和落地方式。"
                ),
                new DevToArticle(
                        topic + " in Production",
                        "https://dev.to/example/in-production",
                        topic + ",production",
                        190,
                        "讨论线上使用时的观测、约束和风险点。"
                )
        );
    }

    private static List<SearchResult> demoReferences(String topic) {
        return List.of(
                new SearchResult(
                        topic + " 官方文档",
                        "https://example.com/docs",
                        "官方文档入口，涵盖核心概念、API 参考、快速开始指南。"
                ),
                new SearchResult(
                        topic + " 架构指南",
                        "https://example.com/guide",
                        "架构视角的深度文章，适合进阶学习。"
                ),
                new SearchResult(
                        topic + " 最佳实践",
                        "https://example.com/best-practices",
                        "社区总结的实战经验与避坑指南。"
                )
        );
    }
}
