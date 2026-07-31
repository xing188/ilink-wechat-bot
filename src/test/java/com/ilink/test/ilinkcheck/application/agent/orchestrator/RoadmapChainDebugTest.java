package com.ilink.test.ilinkcheck.application.agent.orchestrator;

import com.ilink.test.ilinkcheck.application.agent.dto.github.RepoInfo;
import com.ilink.test.ilinkcheck.application.agent.dto.github.TrendingRepo;
import com.ilink.test.ilinkcheck.application.agent.dto.review.ReviewSuggestion;
import com.ilink.test.ilinkcheck.application.agent.dto.roadmap.DevToArticle;
import com.ilink.test.ilinkcheck.application.agent.dto.roadmap.SearchResult;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.model.AgentTaskType;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.model.PhaseExecutor;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.phase.digest.DigestCollectSchedulePhaseExecutor;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.phase.digest.DigestComposePhaseExecutor;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.phase.github.GitHubFetchPhaseExecutor;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.phase.github.GitHubSummarizePhaseExecutor;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.phase.review.ReviewCollectDraftPhaseExecutor;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.phase.review.ReviewFixGeneratePhaseExecutor;
import com.ilink.test.ilinkcheck.application.agent.feature.review.DocReviewPhaseExecutor;
import com.ilink.test.ilinkcheck.application.agent.feature.review.DraftReviewService;
import com.ilink.test.ilinkcheck.application.agent.feature.roadmap.RoadmapComposeService;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.phase.roadmap.RoadmapComposePhaseExecutor;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.phase.roadmap.RoadmapFetchPhaseExecutor;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.routing.IntentRouter;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.runtime.InMemoryAgentStateStore;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.runtime.PhaseExecutorRegistry;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.runtime.WorkflowTemplateRegistry;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.support.AgentTextUtils;
import com.ilink.test.ilinkcheck.application.agent.spi.mcp.DevToArticleProvider;
import com.ilink.test.ilinkcheck.application.agent.spi.mcp.GitHubRepoProvider;
import com.ilink.test.ilinkcheck.application.agent.spi.mcp.WebSearchProvider;
import com.ilink.test.ilinkcheck.application.agent.spi.rag.StyleGuideRetriever;
import com.ilink.test.ilinkcheck.config.AgentMockConfig;
import org.junit.jupiter.api.Test;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

/**
 * 人4 调试用冒烟测试：直接装配 AgentLoopService（不走 Spring / 不调网络），
 * 把"技术路线"链路的真实输出打印出来，并覆盖几个主题提取的边界输入。
 */
class RoadmapChainDebugTest {

    private final PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);

    @Test
    void debugRoadmapChain() throws Exception {
        AgentLoopService agentLoopService = createAgentLoopService();
        IntentRouter intentRouter = new IntentRouter();

        runCase(agentLoopService, intentRouter, "CASE-1 我想学 eBPF，帮我规划学习路线", "我想学 eBPF，帮我规划学习路线");
        runCase(agentLoopService, intentRouter, "CASE-2 学习 RAG", "学习 RAG");
        runCase(agentLoopService, intentRouter, "CASE-3 帮我规划一下 Spring AI 的学习路线", "帮我规划一下 Spring AI 的学习路线");
        runCase(agentLoopService, intentRouter, "CASE-4 只说想学不给主题（应反问问学什么）", "我想学");
        runCase(agentLoopService, intentRouter, "CASE-5 中文主题：我想学 微服务（无空格）", "我想学微服务");
        runCase(agentLoopService, intentRouter, "CASE-5b 小写主题：我想学rag（无空格小写）", "我想学rag");
        runCase(agentLoopService, intentRouter, "CASE-6 非路线意图（应返回 empty）", "北京今天天气怎么样");
    }

    private void runCase(AgentLoopService svc, IntentRouter router, String title, String message) {
        Optional<AgentTaskType> routed = router.route(message);
        Optional<String> topic = AgentTextUtils.extractTopic(message);

        Optional<String> reply = svc.tryHandle("debug-" + title.hashCode(), message);

        out.println("==================================================");
        out.println(">>> " + title);
        out.println(">>> input            = " + message);
        out.println(">>> IntentRouter     = " + routed.map(Enum::name).orElse("<empty 未命中>"));
        out.println(">>> extractTopic      = " + topic.map(t -> "[" + t + "]").orElse("<empty 未提取到>"));
        if (reply.isEmpty()) {
            out.println("<<< [链路未命中] AgentLoopService.tryHandle 返回 empty，交由普通对话/工具链路");
        } else {
            out.println("<<< [链路命中] 输出如下：");
            out.println(reply.get());
        }
        out.println();
        out.flush();
    }

    private AgentLoopService createAgentLoopService() {
        // 复用应用里真实的假数据 Provider，保证看到的输出与启动应用一致
        AgentMockConfig mock = new AgentMockConfig();
        DevToArticleProvider devToArticleProvider = mock.defaultDevToArticleProvider();
        WebSearchProvider webSearchProvider = mock.defaultWebSearchProvider();
        GitHubRepoProvider gitHubRepoProvider = mock.defaultGitHubRepoProvider();
        StyleGuideRetriever styleGuideRetriever = mock.defaultStyleGuideRetriever();
        DraftReviewService draftReviewService = new DraftReviewService(
                styleGuideRetriever,
                new com.ilink.test.ilinkcheck.infrastructure.document.DocumentExportService()
        );

        List<PhaseExecutor> executors = List.of(
                new GitHubFetchPhaseExecutor(gitHubRepoProvider),
                new GitHubSummarizePhaseExecutor(),
                new RoadmapFetchPhaseExecutor(devToArticleProvider, webSearchProvider),
                new RoadmapComposePhaseExecutor(new RoadmapComposeService()),
                new ReviewCollectDraftPhaseExecutor(),
                new DocReviewPhaseExecutor(draftReviewService),
                new ReviewFixGeneratePhaseExecutor(draftReviewService),
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
