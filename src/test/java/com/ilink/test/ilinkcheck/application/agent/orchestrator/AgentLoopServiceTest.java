package com.ilink.test.ilinkcheck.application.agent.orchestrator;

import com.ilink.test.ilinkcheck.application.agent.dto.github.RepoInfo;
import com.ilink.test.ilinkcheck.application.agent.dto.github.TrendingRepo;
import com.ilink.test.ilinkcheck.application.agent.dto.review.ReviewSuggestion;
import com.ilink.test.ilinkcheck.application.agent.dto.roadmap.DevToArticle;
import com.ilink.test.ilinkcheck.application.agent.dto.roadmap.SearchResult;
import com.ilink.test.ilinkcheck.application.agent.feature.review.DocReviewPhaseExecutor;
import com.ilink.test.ilinkcheck.application.agent.feature.review.DraftReviewService;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.model.PhaseExecutor;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.phase.digest.DigestCollectSchedulePhaseExecutor;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.phase.digest.DigestComposePhaseExecutor;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.phase.github.GitHubFetchPhaseExecutor;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.phase.github.GitHubSummarizePhaseExecutor;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.phase.review.ReviewCollectDraftPhaseExecutor;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.phase.review.ReviewConfirmPhaseExecutor;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.phase.review.ReviewFixGeneratePhaseExecutor;
import com.ilink.test.ilinkcheck.application.agent.feature.digest.DailyDigestComposeService;
import com.ilink.test.ilinkcheck.application.agent.feature.digest.DailyDigestSubscriptionService;
import com.ilink.test.ilinkcheck.application.agent.feature.digest.DigestSubscriptionRepository;
import com.ilink.test.ilinkcheck.application.agent.feature.roadmap.MindmapGenerationService;
import com.ilink.test.ilinkcheck.application.agent.feature.roadmap.MindmapImageCache;
import com.ilink.test.ilinkcheck.application.agent.feature.roadmap.RoadmapComposeService;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.phase.roadmap.RoadmapComposePhaseExecutor;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.phase.roadmap.RoadmapConfirmDetailPhaseExecutor;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.phase.roadmap.RoadmapConfirmMindmapPhaseExecutor;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.phase.roadmap.RoadmapConfirmVideoPhaseExecutor;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.phase.roadmap.RoadmapFetchPhaseExecutor;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.routing.IntentRouter;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.runtime.InMemoryAgentStateStore;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.runtime.PhaseExecutorRegistry;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.runtime.WorkflowTemplateRegistry;
import com.ilink.test.ilinkcheck.application.agent.spi.mcp.DevToArticleProvider;
import com.ilink.test.ilinkcheck.application.agent.spi.mcp.GitHubRepoProvider;
import com.ilink.test.ilinkcheck.application.agent.spi.mcp.WebSearchProvider;
import com.ilink.test.ilinkcheck.application.agent.spi.rag.StyleGuideRetriever;
import com.ilink.test.ilinkcheck.adapter.inbound.wechat.WechatBotRuntime;
import com.ilink.test.ilinkcheck.adapter.outbound.wechat.WechatMessageGateway;
import com.ilink.test.ilinkcheck.infrastructure.document.DocumentExportService;
import com.ilink.test.ilinkcheck.infrastructure.persistence.digest.InMemoryDigestSubscriptionRepository;
import com.ilink.test.ilinkcheck.infrastructure.scheduler.digest.TechDigestScheduler;
import com.ilink.test.ilinkcheck.application.tool.categories.translation.TranslationService;
import com.ilink.test.ilinkcheck.infrastructure.document.DocumentExportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.TaskScheduler;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

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
        // 渐进式交互：首条回复是精简版并引导「详细」，第二条才产出完整版
        AgentLoopService agentLoopService = createAgentLoopService();

        Optional<String> firstReply = agentLoopService.tryHandle("conv-2", "我想学 eBPF，帮我规划学习路线");

        assertThat(firstReply).isPresent();
        assertThat(firstReply.get()).contains("eBPF 学习路线");
        assertThat(firstReply.get()).contains("详细");
        assertThat(firstReply.get()).doesNotContain("第一阶段：预备知识");

        Optional<String> secondReply = agentLoopService.tryHandle("conv-2", "详细");

        assertThat(secondReply).isPresent();
        assertThat(secondReply.get()).contains("eBPF 学习路线");
        assertThat(secondReply.get()).contains("第一阶段：预备知识");
        assertThat(secondReply.get()).contains("推荐资料");
    }

    @Test
    void shouldEndRoadmapGracefullyWhenUserDeclinesDetailed() {
        // 用户在精简版后回复「不用了」，应礼貌结束而不强制推送完整版
        AgentLoopService agentLoopService = createAgentLoopService();

        agentLoopService.tryHandle("conv-12", "我想学 eBPF，帮我规划学习路线");
        Optional<String> reply = agentLoopService.tryHandle("conv-12", "不用了");

        assertThat(reply).isPresent();
        assertThat(reply.get()).doesNotContain("第一阶段：预备知识");
        assertThat(reply.get()).contains("完整版路线先不发了");
    }

    @Test
    void shouldOfferBilibiliVideoLinksThenAskForMindmap() {
        // 渐进式交互第三屏：完整版后询问视频，用户确认后发送视频链接并继续询问思维导图
        AgentLoopService agentLoopService = createAgentLoopService();

        agentLoopService.tryHandle("conv-20", "我想学 eBPF，帮我规划学习路线");
        Optional<String> detailedReply = agentLoopService.tryHandle("conv-20", "详细");

        assertThat(detailedReply).isPresent();
        assertThat(detailedReply.get()).contains("B 站网课");
        assertThat(detailedReply.get()).contains("思维导图");

        Optional<String> videoReply = agentLoopService.tryHandle("conv-20", "视频");

        assertThat(videoReply).isPresent();
        assertThat(videoReply.get()).contains("配套 B 站网课视频");
        assertThat(videoReply.get()).contains("bilibili.com");
        assertThat(videoReply.get()).contains("来源");
        assertThat(videoReply.get()).contains("思维导图");
    }

    @Test
    void shouldSkipBothExtrasGracefully() {
        // 完整版后用户都不要补充资料，应礼貌结束而不强制推送视频或思维导图
        AgentLoopService agentLoopService = createAgentLoopService();

        agentLoopService.tryHandle("conv-21", "我想学 eBPF，帮我规划学习路线");
        agentLoopService.tryHandle("conv-21", "详细");
        Optional<String> reply = agentLoopService.tryHandle("conv-21", "不用了");

        assertThat(reply).isPresent();
        assertThat(reply.get()).contains("补充资料先不发了");
        assertThat(reply.get()).doesNotContain("bilibili.com");
    }

    @Test
    void shouldJumpToMindmapWhenUserRequestsItDirectlyAfterDetail() {
        // 完整版后用户直接回复「思维导图」，不应被网课环节卡住，
        // 应直接推进到 MINDMAP 阶段并在同一轮请求内返回 [MINDMAP_IMAGE] 标记（不再需要二次输入）。
        AgentLoopService agentLoopService = createAgentLoopService();

        agentLoopService.tryHandle("conv-21b", "我想学 eBPF，帮我规划学习路线");
        agentLoopService.tryHandle("conv-21b", "详细");
        Optional<String> reply = agentLoopService.tryHandle("conv-21b", "思维导图");

        assertThat(reply).isPresent();
        // VideoPhaseExecutor 用 next() 直接推进到 MINDMAP 阶段，主循环继续执行 MindmapPhaseExecutor
        assertThat(reply.get()).startsWith("[MINDMAP_IMAGE]:");
    }

    @Test
    void shouldGenerateMindmapImageWhenUserRequestsIt() {
        // 渐进式交互第四屏：看了视频后用户回复「思维导图」，生成图片版导图并返回 [MINDMAP_IMAGE] 标记
        AgentLoopService agentLoopService = createAgentLoopService();

        agentLoopService.tryHandle("conv-22", "我想学 eBPF，帮我规划学习路线");
        agentLoopService.tryHandle("conv-22", "详细");
        agentLoopService.tryHandle("conv-22", "视频"); // 先看视频
        Optional<String> reply = agentLoopService.tryHandle("conv-22", "思维导图");

        assertThat(reply).isPresent();
        assertThat(reply.get()).startsWith("[MINDMAP_IMAGE]:");
    }

    @Test
    void shouldSkipMindmapWhenUserDeclines() {
        // 询问思维导图后用户拒绝，应礼貌结束
        AgentLoopService agentLoopService = createAgentLoopService();

        agentLoopService.tryHandle("conv-23", "我想学 eBPF，帮我规划学习路线");
        agentLoopService.tryHandle("conv-23", "详细");
        agentLoopService.tryHandle("conv-23", "视频"); // 看了视频
        Optional<String> reply = agentLoopService.tryHandle("conv-23", "不用");

        assertThat(reply).isPresent();
        assertThat(reply.get()).contains("思维导图先不发了");
        assertThat(reply.get()).doesNotContain("知识点思维导图");
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
        assertThat(secondReply.get()).contains("需要我帮你发送修改好的稿件吗");
    }

    @Test
    void shouldSendCorrectedDraftAfterConfirmation() {
        AgentLoopService agentLoopService = createAgentLoopService();

        agentLoopService.tryHandle("conv-10", "帮我审一下这篇博客");
        agentLoopService.tryHandle("conv-10", """
                使用redis缓存和Java8版本。

                代码：
                ```
                INFO commandstats
                ```
                """);

        Optional<String> reply = agentLoopService.tryHandle("conv-10", "好");

        assertThat(reply).isPresent();
        assertThat(reply.get()).contains("已按规范帮你优化稿件");
        assertThat(reply.get()).contains("Redis 缓存");
        assertThat(reply.get()).contains("Java8 版本");
        assertThat(reply.get()).contains("```text");
    }

    @Test
    void shouldSkipCorrectedDraftWhenUserRejects() {
        AgentLoopService agentLoopService = createAgentLoopService();

        agentLoopService.tryHandle("conv-11", "帮我审一下这篇博客");
        agentLoopService.tryHandle("conv-11", "使用redis缓存和Java8版本。\n\n代码：\n```\nINFO commandstats\n```");

        Optional<String> reply = agentLoopService.tryHandle("conv-11", "不用");

        assertThat(reply).isPresent();
        assertThat(reply.get()).contains("已跳过修改稿");
        assertThat(reply.get()).doesNotContain("已按规范帮你优化稿件");
    }

    @Test
    void shouldAskForTimeThenCreateDigestPreview() {
        AgentLoopService agentLoopService = createAgentLoopService();

        Optional<String> firstReply = agentLoopService.tryHandle("conv-4", "帮我订阅技术晨报");
        Optional<String> secondReply = agentLoopService.tryHandle("conv-4", "每天 08:30 推送");

        assertThat(firstReply).isPresent();
        assertThat(firstReply.get()).contains("推送技术晨报");
        assertThat(secondReply).isPresent();
        assertThat(secondReply.get()).contains("已为你记录技术推送计划：每天 08:30");
        assertThat(secondReply.get()).contains("推送预览");
        assertThat(secondReply.get()).contains("今日新增 Star");
    }

    @Test
    void shouldAskForMeridiemWhenDigestTimeIsAmbiguousThenAcceptClarification() {
        AgentLoopService agentLoopService = createAgentLoopService();

        Optional<String> firstReply = agentLoopService.tryHandle("conv-10", "帮我订阅技术晨报，9点推送");
        Optional<String> secondReply = agentLoopService.tryHandle("conv-10", "上午");

        assertThat(firstReply).isPresent();
        assertThat(firstReply.get()).contains("还不确定是上午还是下午");
        assertThat(secondReply).isPresent();
        assertThat(secondReply.get()).contains("已为你记录技术推送计划：每天 09:00");
    }

    @Test
    void shouldJumpToNewRouteWhenDifferentIntentArrivesDuringDigestWorkflow() {
        AgentLoopService agentLoopService = createAgentLoopService();

        Optional<String> firstReply = agentLoopService.tryHandle("conv-11", "帮我订阅技术晨报");
        Optional<String> secondReply = agentLoopService.tryHandle("conv-11", "帮我速读一下 https://github.com/spring-projects/spring-ai");

        assertThat(firstReply).isPresent();
        assertThat(firstReply.get()).contains("推送技术晨报");
        assertThat(secondReply).isPresent();
        assertThat(secondReply.get()).contains("GitHub 仓库速读");
        assertThat(secondReply.get()).contains("spring-projects/spring-ai");
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

        WebSearchProvider webSearchProvider = (query, limit) -> {
            // 视频检索：查询含 bilibili / B站 时返回 B 站网课链接，用于验证「网课视频」分支
            if (query.contains("bilibili") || query.contains("B站")) {
                return List.of(
                        new SearchResult(query + " 入门到实战", "https://www.bilibili.com/video/BV1abc", "UP 主系统讲解，从环境搭建到项目实战。"),
                        new SearchResult(query + " 核心原理", "https://www.bilibili.com/video/BV2def", "图解核心原理与设计思想。"),
                        new SearchResult(query + " 项目实战", "https://www.bilibili.com/video/BV3ghi", "手把手带你做一个最小可运行项目。")
                ).stream().limit(limit).toList();
            }
            return List.of(
                    new SearchResult(query + " official docs", "https://example.com/docs", "官方文档入口。"),
                    new SearchResult(query + " guide", "https://example.com/guide", "补充阅读。")
            ).stream().limit(limit).toList();
        };

        StyleGuideRetriever styleGuideRetriever = draftText -> List.of(
                new ReviewSuggestion("排版", "正文前半段", "检测到中英文直接贴合的写法。", "建议改成 `Redis 延迟`。"),
                new ReviewSuggestion("术语", "全文", "检测到 `redis` 小写写法。", "建议统一写成 `Redis`。")
        );

        DraftReviewService draftReviewService = new DraftReviewService(styleGuideRetriever, new DocumentExportService());
        DailyDigestComposeService digestComposeService = new DailyDigestComposeService(
                gitHubRepoProvider,
                devToArticleProvider,
                "java",
                "daily",
                3,
                "ai",
                3
        );
        DigestSubscriptionRepository digestSubscriptionRepository = new InMemoryDigestSubscriptionRepository();
        @SuppressWarnings("unchecked")
        ObjectProvider<WechatBotRuntime> runtimeProvider = mock(ObjectProvider.class);
        TechDigestScheduler techDigestScheduler = new TechDigestScheduler(
                digestSubscriptionRepository,
                runtimeProvider,
                new WechatMessageGateway(),
                mock(TaskScheduler.class),
                digestComposeService
        );
        DailyDigestSubscriptionService dailyDigestSubscriptionService = new DailyDigestSubscriptionService(
                digestSubscriptionRepository,
                techDigestScheduler,
                Clock.systemDefaultZone(),
                "Asia/Shanghai"
        );

        List<PhaseExecutor> executors = List.of(
                new GitHubFetchPhaseExecutor(gitHubRepoProvider),
                new GitHubSummarizePhaseExecutor(),
                new RoadmapFetchPhaseExecutor(devToArticleProvider, webSearchProvider),
                new RoadmapComposePhaseExecutor(composeService),
                new RoadmapConfirmDetailPhaseExecutor(composeService),
                new RoadmapConfirmVideoPhaseExecutor(webSearchProvider, composeService),
                new RoadmapConfirmMindmapPhaseExecutor(new MindmapGenerationService(), new MindmapImageCache()),
                new ReviewConfirmPhaseExecutor(),
                new ReviewCollectDraftPhaseExecutor(),
                new DocReviewPhaseExecutor(draftReviewService),
                new ReviewFixGeneratePhaseExecutor(draftReviewService),
                new DigestCollectSchedulePhaseExecutor(),
                new DigestComposePhaseExecutor(digestComposeService, dailyDigestSubscriptionService)
        );

        return new AgentLoopService(
                new IntentRouter(),
                new WorkflowTemplateRegistry(),
                new PhaseExecutorRegistry(executors),
                new InMemoryAgentStateStore()
        );
    }
}
