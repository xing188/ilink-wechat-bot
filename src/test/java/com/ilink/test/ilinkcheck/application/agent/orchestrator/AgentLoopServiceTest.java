package com.ilink.test.ilinkcheck.application.agent.orchestrator;

import com.ilink.test.ilinkcheck.application.agent.dto.github.RepoInfo;
import com.ilink.test.ilinkcheck.application.agent.dto.github.TrendingRepo;
import com.ilink.test.ilinkcheck.application.agent.dto.review.ReviewSuggestion;
import com.ilink.test.ilinkcheck.application.agent.dto.roadmap.DevToArticle;
import com.ilink.test.ilinkcheck.application.agent.dto.roadmap.SearchResult;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.model.PhaseExecutor;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.phase.digest.DigestCollectSchedulePhaseExecutor;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.phase.digest.DigestComposePhaseExecutor;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.phase.github.GitHubFetchPhaseExecutor;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.phase.github.GitHubSummarizePhaseExecutor;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.phase.review.ReviewCollectDraftPhaseExecutor;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.phase.review.ReviewConfirmPhaseExecutor;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.phase.review.ReviewGeneratePhaseExecutor;
import com.ilink.test.ilinkcheck.application.agent.feature.roadmap.RoadmapComposeService;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.phase.roadmap.RoadmapComposePhaseExecutor;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.phase.roadmap.RoadmapFetchPhaseExecutor;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.routing.IntentRouter;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.runtime.InMemoryAgentStateStore;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.runtime.PhaseExecutorRegistry;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.runtime.WorkflowTemplateRegistry;
import com.ilink.test.ilinkcheck.application.agent.spi.mcp.DevToArticleProvider;
import com.ilink.test.ilinkcheck.application.agent.spi.mcp.GitHubRepoProvider;
import com.ilink.test.ilinkcheck.application.agent.spi.mcp.WebSearchProvider;
import com.ilink.test.ilinkcheck.application.agent.spi.rag.StyleGuideRetriever;
import com.ilink.test.ilinkcheck.application.tool.categories.translation.TranslationService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Agent 大循环最小可运行测试。
 * 这组测试不是测外部 API，而是验证：
 * 1. 路由能否命中正确任务；
 * 2. 阶段是否能正确推进；
 * 3. 多轮输入是否能继续执行；
 * 4. 最终回复是否按预期生成。
 */
class AgentLoopServiceTest {

    @Test
    void shouldSummarizeGitHubRepository() {
        AgentLoopService agentLoopService = createAgentLoopService();

        Optional<String> reply = agentLoopService.tryHandle("conv-1", "帮我速读一下 https://github.com/spring-projects/spring-ai");

        assertThat(reply).isPresent();
        assertThat(reply.get()).contains("GitHub 仓库速读");
        assertThat(reply.get()).contains("spring-projects/spring-ai");
        assertThat(reply.get()).contains("README 提炼");
    }

    @Test
    void shouldGenerateTechRoadmap() {
        AgentLoopService agentLoopService = createAgentLoopService();

        Optional<String> reply = agentLoopService.tryHandle("conv-2", "我想学 eBPF，帮我规划学习路线");

        assertThat(reply).isPresent();
        assertThat(reply.get()).contains("eBPF 学习路线");
        assertThat(reply.get()).contains("第一阶段：预备知识");
        assertThat(reply.get()).contains("推荐资料");
    }

    @Test
    void shouldRouteBareLearningPhrase() {
        // 锁定修复 #1：裸“学习 + 主题”此前不命中路由
        AgentLoopService agentLoopService = createAgentLoopService();

        Optional<String> reply = agentLoopService.tryHandle("conv-6", "学习 RAG");

        assertThat(reply).isPresent();
        assertThat(reply.get()).contains("RAG 学习路线");
    }

    @Test
    void shouldSupportChineseTopic() {
        // 锁定修复 #2：中文主题此前抽不到，会被反问
        AgentLoopService agentLoopService = createAgentLoopService();

        Optional<String> reply = agentLoopService.tryHandle("conv-7", "我想学 微服务");

        assertThat(reply).isPresent();
        assertThat(reply.get()).contains("微服务 学习路线");
    }

    @Test
    void shouldAskForDraftThenGenerateReviewReport() {
        AgentLoopService agentLoopService = createAgentLoopService();

        Optional<String> firstReply = agentLoopService.tryHandle("conv-3", "帮我审一下这篇博客");
        Optional<String> secondReply = agentLoopService.tryHandle("conv-3", """
                这是我写的 Redis延迟排查草稿。

                ```
                INFO commandstats
                ```

                这篇文章主要讨论 redis延迟 的排查路径，以及怎么验证瓶颈。
                """);

        assertThat(firstReply).isPresent();
        assertThat(firstReply.get()).contains("请把要审的技术文档或博客草稿直接贴给我");
        assertThat(secondReply).isPresent();
        assertThat(secondReply.get()).contains("技术文档审稿报告");
        assertThat(secondReply.get()).contains("具体建议");
    }

    @Test
    void shouldAskForTimeThenCreateDigestPreview() {
        AgentLoopService agentLoopService = createAgentLoopService();

        Optional<String> firstReply = agentLoopService.tryHandle("conv-4", "帮我订阅技术晨报");
        Optional<String> secondReply = agentLoopService.tryHandle("conv-4", "每天 08:30 推送");

        assertThat(firstReply).isPresent();
        assertThat(firstReply.get()).contains("每天几点推送技术晨报");
        assertThat(secondReply).isPresent();
        assertThat(secondReply.get()).contains("已为你记录技术晨报订阅时间：08:30");
        assertThat(secondReply.get()).contains("晨报预览");
        assertThat(secondReply.get()).contains("今日新增 Star");
    }

    @Test
    void shouldReturnEmptyWhenMessageIsNotAgentIntent() {
        AgentLoopService agentLoopService = createAgentLoopService();

        Optional<String> reply = agentLoopService.tryHandle("conv-5", "北京今天天气怎么样");

        assertThat(reply).isEmpty();
    }

    @Test
    void shouldTranslateRoadmapOnFollowUpCommand() {
        // 锁定新功能：先生成英文路线，再发"翻译成中文"应复用上次资料并重渲染为中文
        RoadmapComposeService composeService = new RoadmapComposeService();
        composeService.setTranslationService(new TranslationService() {
            @Override public String translate(String text, String src, String tgt) { return "【译】" + text; }
            @Override public java.util.List<String> batchTranslate(java.util.List<String> texts, String src, String tgt) {
                return texts.stream().map(t -> "【译】" + t).toList();
            }
        });
        AgentLoopService agentLoopService = createAgentLoopService(composeService);

        agentLoopService.tryHandle("conv-8", "我想学 eBPF，帮我规划学习路线");
        Optional<String> reply = agentLoopService.tryHandle("conv-8", "翻译成中文");

        assertThat(reply).isPresent();
        assertThat(reply.get()).contains("eBPF 学习路线");
        assertThat(reply.get()).contains("【译】");
    }

    @Test
    void shouldRestartRoadmapWhenNewTopicAfterFinished() {
        // 已完成路线后收到新的学习主题，应开启全新路线而非复用旧结果
        AgentLoopService agentLoopService = createAgentLoopService();

        agentLoopService.tryHandle("conv-9", "我想学 eBPF，帮我规划学习路线");
        Optional<String> reply = agentLoopService.tryHandle("conv-9", "我想学 RAG");

        assertThat(reply).isPresent();
        assertThat(reply.get()).contains("RAG 学习路线");
    }

    private AgentLoopService createAgentLoopService() {
        return createAgentLoopService(new RoadmapComposeService());
    }

    private AgentLoopService createAgentLoopService(RoadmapComposeService composeService) {
        GitHubRepoProvider gitHubRepoProvider = new GitHubRepoProvider() {
            @Override
            public RepoInfo fetchRepo(String owner, String repo) {
                return new RepoInfo(
                        owner,
                        repo,
                        "Spring 生态下的 AI 应用开发框架。",
                        "Java",
                        5200,
                        860,
                        "README 主要介绍了模型接入、向量库、RAG 和工具调用的集成方式。",
                        "Java / Spring Boot / Spring AI"
                );
            }

            @Override
            public List<TrendingRepo> fetchTrending(String language, String since) {
                return List.of(
                        new TrendingRepo("spring-projects", "spring-ai", "Spring 生态下的 AI 应用开发框架。", "Java", 320, "https://github.com/spring-projects/spring-ai", TrendingRepo.MetricType.DAILY_STARS, TrendingRepo.Source.MOCK),
                        new TrendingRepo("langchain-ai", "langgraph", "面向多 Agent 工作流的图式编排框架。", "Python", 280, "https://github.com/langchain-ai/langgraph", TrendingRepo.MetricType.DAILY_STARS, TrendingRepo.Source.MOCK)
                );
            }
        };

        DevToArticleProvider devToArticleProvider = (tag, limit) -> List.of(
                new DevToArticle(tag + " for Beginners", "https://dev.to/example/1", tag + ",tutorial", 300, "一篇入门文章。"),
                new DevToArticle("Practical " + tag, "https://dev.to/example/2", tag + ",practice", 240, "一篇实战文章。")
        ).stream().limit(limit).toList();

        WebSearchProvider webSearchProvider = (query, limit) -> List.of(
                new SearchResult(query + " official docs", "https://example.com/docs", "官方文档入口。"),
                new SearchResult(query + " guide", "https://example.com/guide", "补充阅读。")
        ).stream().limit(limit).toList();

        StyleGuideRetriever styleGuideRetriever = draftText -> List.of(
                new ReviewSuggestion("排版", "正文前半段", "检测到中英文直接贴合的写法。", "建议改成 `Redis 延迟`。"),
                new ReviewSuggestion("术语", "全文", "检测到 `redis` 小写写法。", "建议统一写成 `Redis`。")
        );

        List<PhaseExecutor> executors = List.of(
                new GitHubFetchPhaseExecutor(gitHubRepoProvider),
                new GitHubSummarizePhaseExecutor(),
                new RoadmapFetchPhaseExecutor(devToArticleProvider, webSearchProvider),
                new RoadmapComposePhaseExecutor(composeService),
                new ReviewConfirmPhaseExecutor(),
                new ReviewCollectDraftPhaseExecutor(),
                new ReviewGeneratePhaseExecutor(styleGuideRetriever),
                new DigestCollectSchedulePhaseExecutor(),
                new DigestComposePhaseExecutor(gitHubRepoProvider, devToArticleProvider)
        );

        return new AgentLoopService(
                new IntentRouter(),
                new WorkflowTemplateRegistry(),
                new PhaseExecutorRegistry(executors),
                new InMemoryAgentStateStore()
        );
    }
}
