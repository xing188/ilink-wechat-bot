package com.ilink.test.ilinkcheck.infrastructure.scheduler.digest;

import com.github.wechat.ilink.sdk.ILinkClient;
import com.ilink.test.ilinkcheck.adapter.inbound.wechat.WechatBotRuntime;
import com.ilink.test.ilinkcheck.adapter.outbound.wechat.WechatMessageGateway;
import com.ilink.test.ilinkcheck.application.agent.dto.digest.DigestSubscription;
import com.ilink.test.ilinkcheck.application.agent.dto.github.RepoInfo;
import com.ilink.test.ilinkcheck.application.agent.dto.github.RepoTreeNode;
import com.ilink.test.ilinkcheck.application.agent.dto.github.TrendingRepo;
import com.ilink.test.ilinkcheck.application.agent.dto.roadmap.DevToArticle;
import com.ilink.test.ilinkcheck.application.agent.feature.digest.DailyDigestComposeService;
import com.ilink.test.ilinkcheck.application.agent.spi.mcp.DevToArticleProvider;
import com.ilink.test.ilinkcheck.application.agent.spi.mcp.GitHubRepoProvider;
import com.ilink.test.ilinkcheck.application.reminder.model.ReminderRecurrence;
import com.ilink.test.ilinkcheck.infrastructure.persistence.digest.InMemoryDigestSubscriptionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.TaskScheduler;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TechDigestSchedulerTest {

    @Test
    void shouldRegisterCronSubscriptionTask() {
        InMemoryDigestSubscriptionRepository repository = new InMemoryDigestSubscriptionRepository();
        @SuppressWarnings("unchecked")
        ObjectProvider<WechatBotRuntime> runtimeProvider = mock(ObjectProvider.class);
        TaskScheduler taskScheduler = mock(TaskScheduler.class);
        @SuppressWarnings("unchecked")
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        doReturn(future).when(taskScheduler)
                .schedule(any(Runnable.class), any(org.springframework.scheduling.Trigger.class));

        TechDigestScheduler scheduler = new TechDigestScheduler(
                repository,
                runtimeProvider,
                new WechatMessageGateway(),
                taskScheduler,
                composeService()
        );

        DigestSubscription subscription = new DigestSubscription(
                "user-1",
                "每周二 08:30",
                "Asia/Shanghai",
                ReminderRecurrence.CRON,
                Instant.parse("2026-08-04T00:30:00Z"),
                "0 30 8 ? * TUE",
                null,
                null,
                Instant.parse("2026-07-31T08:00:00Z"),
                Instant.parse("2026-07-31T08:00:00Z")
        );

        scheduler.scheduleSubscription(subscription);

        assertThat(scheduler.isScheduled("user-1")).isTrue();
        verify(taskScheduler).schedule(any(Runnable.class), any(org.springframework.scheduling.Trigger.class));
    }

    @Test
    void shouldTriggerNowAndSendDigestWhenClientIsReady() {
        InMemoryDigestSubscriptionRepository repository = new InMemoryDigestSubscriptionRepository();
        DigestSubscription subscription = new DigestSubscription(
                "user-1",
                "每天 08:30",
                "Asia/Shanghai",
                ReminderRecurrence.DAILY,
                Instant.parse("2026-07-31T08:00:00Z"),
                null,
                null,
                null,
                Instant.parse("2026-07-31T08:00:00Z"),
                Instant.parse("2026-07-31T08:00:00Z")
        );
        repository.save(subscription);

        WechatBotRuntime runtime = mock(WechatBotRuntime.class);
        ILinkClient client = mock(ILinkClient.class);
        when(runtime.currentClient()).thenReturn(Optional.of(client));

        @SuppressWarnings("unchecked")
        ObjectProvider<WechatBotRuntime> runtimeProvider = mock(ObjectProvider.class);
        when(runtimeProvider.getIfAvailable()).thenReturn(runtime);

        WechatMessageGateway gateway = mock(WechatMessageGateway.class);
        when(gateway.sendTextIfPossible(eq(client), eq("user-1"), any(String.class))).thenReturn(true);

        TechDigestScheduler scheduler = new TechDigestScheduler(
                repository,
                runtimeProvider,
                gateway,
                mock(TaskScheduler.class),
                composeService()
        );

        boolean triggered = scheduler.triggerNow("user-1");

        assertThat(triggered).isTrue();
        verify(gateway).sendTextIfPossible(eq(client), eq("user-1"), any(String.class));
    }

    private DailyDigestComposeService composeService() {
        GitHubRepoProvider gitHubRepoProvider = new GitHubRepoProvider() {
            @Override
            public RepoInfo fetchRepo(String owner, String repo) {
                return null;
            }

            @Override
            public List<TrendingRepo> fetchTrending(String language, String since) {
                return List.of(
                        new TrendingRepo("spring-projects", "spring-ai", "Spring AI project", "Java", 320,
                                "https://github.com/spring-projects/spring-ai",
                                TrendingRepo.MetricType.DAILY_STARS, TrendingRepo.Source.MOCK)
                );
            }

            @Override
            public List<RepoTreeNode> fetchDirectoryTree(String owner, String repo, String path) {
                return List.of();
            }
        };
        DevToArticleProvider devToArticleProvider = (tag, limit) -> List.of(
                new DevToArticle("AI article", "https://dev.to/example/ai", "ai", 100, "article")
        );
        return new DailyDigestComposeService(gitHubRepoProvider, devToArticleProvider, "java", "daily", 3, "ai", 3);
    }
}
