package com.ilink.test.ilinkcheck.application.agent.orchestrator.phase.digest;

import com.ilink.test.ilinkcheck.adapter.inbound.wechat.WechatBotRuntime;
import com.ilink.test.ilinkcheck.adapter.outbound.wechat.WechatMessageGateway;
import com.ilink.test.ilinkcheck.application.agent.dto.digest.DigestSubscription;
import com.ilink.test.ilinkcheck.application.agent.dto.github.RepoInfo;
import com.ilink.test.ilinkcheck.application.agent.dto.github.TrendingRepo;
import com.ilink.test.ilinkcheck.application.agent.dto.roadmap.DevToArticle;
import com.ilink.test.ilinkcheck.application.agent.feature.digest.DailyDigestComposeService;
import com.ilink.test.ilinkcheck.application.agent.feature.digest.DailyDigestSubscriptionService;
import com.ilink.test.ilinkcheck.application.agent.feature.digest.DigestSubscriptionRepository;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.model.AgentArtifacts;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.model.AgentPhase;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.model.AgentState;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.model.AgentTaskType;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.model.PhaseExecutionResult;
import com.ilink.test.ilinkcheck.application.agent.spi.mcp.DevToArticleProvider;
import com.ilink.test.ilinkcheck.application.agent.spi.mcp.GitHubRepoProvider;
import com.ilink.test.ilinkcheck.infrastructure.persistence.digest.InMemoryDigestSubscriptionRepository;
import com.ilink.test.ilinkcheck.infrastructure.scheduler.digest.TechDigestScheduler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.TaskScheduler;

import java.time.Clock;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DigestComposePhaseExecutorTest {

    @Test
    void shouldShowCleanPreviewAndCreateSubscription() {
        GitHubRepoProvider gitHubRepoProvider = new GitHubRepoProvider() {
            @Override
            public RepoInfo fetchRepo(String owner, String repo) {
                return new RepoInfo(
                        owner,
                        repo,
                        "Spring AI 是一个用于构建 AI 应用的开源项目。",
                        "Java",
                        999,
                        45,
                        "Spring AI 是一个用于构建 AI 应用的开源项目。",
                        "Java / Spring Boot",
                        ""
                );
            }

            @Override
            public List<TrendingRepo> fetchTrending(String language, String since) {
                return List.of(
                        new TrendingRepo(
                                "spring-projects",
                                "spring-ai",
                                "Trending description",
                                "Java",
                                999,
                                "https://github.com/spring-projects/spring-ai",
                                TrendingRepo.MetricType.TOTAL_STARS,
                                TrendingRepo.Source.GITHUB_SEARCH_API
                        )
                );
            }
        };
        DevToArticleProvider devToArticleProvider = (tag, limit) -> List.of(
                new DevToArticle("AI article", "https://dev.to/example/ai", "ai", 100, "一篇 AI 入门文章。")
        );

        DailyDigestComposeService composeService = new DailyDigestComposeService(
                gitHubRepoProvider,
                devToArticleProvider,
                "java",
                "weekly",
                3,
                "ai",
                3
        );
        DigestSubscriptionRepository repository = new InMemoryDigestSubscriptionRepository();
        @SuppressWarnings("unchecked")
        ObjectProvider<WechatBotRuntime> runtimeProvider = mock(ObjectProvider.class);
        TechDigestScheduler scheduler = new TechDigestScheduler(
                repository,
                runtimeProvider,
                new WechatMessageGateway(),
                mock(TaskScheduler.class),
                composeService
        );
        DailyDigestSubscriptionService subscriptionService = new DailyDigestSubscriptionService(
                repository,
                scheduler,
                Clock.systemDefaultZone(),
                "Asia/Shanghai"
        );

        DigestComposePhaseExecutor executor = new DigestComposePhaseExecutor(composeService, subscriptionService);
        AgentState state = AgentState.newWorkflow("conv-digest", AgentTaskType.DAILY_DIGEST_SUBSCRIBE, AgentPhase.DIGEST_COMPOSE, "订阅晨报");
        state.putArtifact(AgentArtifacts.REQUEST_SCHEDULE_TIME, "08:30");

        PhaseExecutionResult result = executor.execute(state);

        assertThat(result.replyToUser()).contains("已为你记录技术推送计划：每天 08:30");
        assertThat(result.replyToUser()).contains("- spring-projects/spring-ai（当前 Star: 999）：Spring AI 是一个用于构建 AI 应用的开源项目。链接：https://github.com/spring-projects/spring-ai");
        assertThat(subscriptionService.findByUserId("conv-digest")).isPresent();
    }
}
