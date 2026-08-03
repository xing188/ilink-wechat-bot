package com.ilink.test.ilinkcheck.application.tool.categories.digest;

import com.ilink.test.ilinkcheck.adapter.inbound.wechat.WechatBotRuntime;
import com.ilink.test.ilinkcheck.adapter.outbound.wechat.WechatMessageGateway;
import com.ilink.test.ilinkcheck.application.agent.dto.github.RepoInfo;
import com.ilink.test.ilinkcheck.application.agent.dto.github.TrendingRepo;
import com.ilink.test.ilinkcheck.application.agent.dto.roadmap.DevToArticle;
import com.ilink.test.ilinkcheck.application.agent.feature.digest.DailyDigestComposeService;
import com.ilink.test.ilinkcheck.application.agent.feature.digest.DailyDigestSubscriptionService;
import com.ilink.test.ilinkcheck.application.agent.feature.digest.DigestSubscriptionRepository;
import com.ilink.test.ilinkcheck.application.agent.spi.mcp.DevToArticleProvider;
import com.ilink.test.ilinkcheck.application.agent.spi.mcp.GitHubRepoProvider;
import com.ilink.test.ilinkcheck.application.reminder.context.ReminderRequestContext;
import com.ilink.test.ilinkcheck.infrastructure.persistence.digest.InMemoryDigestSubscriptionRepository;
import com.ilink.test.ilinkcheck.infrastructure.scheduler.digest.TechDigestScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.TaskScheduler;

import java.time.Clock;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DailyDigestToolTest {

    @AfterEach
    void clearContext() {
        ReminderRequestContext.clear();
    }

    @Test
    void shouldSubscribeDailyDigestWhenTimeIsValid() {
        TestContext context = createContext();
        ReminderRequestContext.setCurrentUserId("user-1");
        DailyDigestTool tool = new DailyDigestTool(context.composeService(), context.subscriptionService());

        String reply = tool.subscribeDailyDigest("每天 09:00");

        assertThat(reply).contains("已为你记录技术推送计划：每天 09:00");
        assertThat(reply).contains("- spring-projects/spring-ai（本周新增 Star: 320）：Spring AI 是一个用于构建 AI 应用的开源项目。链接：https://github.com/spring-projects/spring-ai");
        assertThat(reply).contains("- AI article（点赞: 100）：一篇 AI 入门文章。");
        assertThat(context.subscriptionService().findByUserId("user-1")).isPresent();
    }

    @Test
    void shouldRejectInvalidScheduleTime() {
        TestContext context = createContext();
        DailyDigestTool tool = new DailyDigestTool(context.composeService(), context.subscriptionService());

        String reply = tool.subscribeDailyDigest("明天");

        assertThat(reply).contains("几点执行");
    }

    @Test
    void shouldAskForMeridiemWhenScheduleTimeIsAmbiguous() {
        TestContext context = createContext();
        DailyDigestTool tool = new DailyDigestTool(context.composeService(), context.subscriptionService());

        String reply = tool.subscribeDailyDigest("9点");

        assertThat(reply).contains("上午还是下午");
    }

    @Test
    void shouldPreviewOnlyWhenNoUserContext() {
        TestContext context = createContext();
        DailyDigestTool tool = new DailyDigestTool(context.composeService(), context.subscriptionService());

        String reply = tool.subscribeDailyDigest("08:30");

        assertThat(reply).contains("已为你记录技术推送计划：每天 08:30");
        assertThat(reply).contains("当前没有可用的用户上下文");
        assertThat(context.subscriptionService().findAllSubscriptions()).isEmpty();
    }

    @Test
    void shouldPreviewDailyDigestWithoutSubscriptionTime() {
        TestContext context = createContext();
        DailyDigestTool tool = new DailyDigestTool(context.composeService(), context.subscriptionService());

        String reply = tool.previewDailyDigest();

        assertThat(reply).contains("已为你记录技术推送计划：未设置");
        assertThat(reply).contains("GitHub 项目");
        assertThat(reply).contains("技术文章");
    }

    private TestContext createContext() {
        GitHubRepoProvider gitHubRepoProvider = new GitHubRepoProvider() {
            @Override
            public RepoInfo fetchRepo(String owner, String repo) {
                return new RepoInfo(
                        owner,
                        repo,
                        "Spring AI 是一个用于构建 AI 应用的开源项目。",
                        "Java",
                        5200,
                        860,
                        "Spring AI 是一个用于构建 AI 应用的开源项目。",
                        "Java / Spring Boot / Spring AI"
                );
            }

            @Override
            public List<TrendingRepo> fetchTrending(String language, String since) {
                return List.of(
                        new TrendingRepo(
                                "spring-projects",
                                "spring-ai",
                                "Spring AI 是一个用于构建 AI 应用的开源项目。",
                                "Java",
                                320,
                                "https://github.com/spring-projects/spring-ai",
                                TrendingRepo.MetricType.WEEKLY_STARS,
                                TrendingRepo.Source.MOCK
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
        return new TestContext(composeService, subscriptionService);
    }

    private record TestContext(
            DailyDigestComposeService composeService,
            DailyDigestSubscriptionService subscriptionService
    ) {
    }
}
