package com.ilink.test.ilinkcheck.application.agent.feature.digest;

import com.ilink.test.ilinkcheck.adapter.inbound.wechat.WechatBotRuntime;
import com.ilink.test.ilinkcheck.adapter.outbound.wechat.WechatMessageGateway;
import com.ilink.test.ilinkcheck.application.agent.dto.github.RepoInfo;
import com.ilink.test.ilinkcheck.application.agent.dto.github.TrendingRepo;
import com.ilink.test.ilinkcheck.application.agent.dto.roadmap.DevToArticle;
import com.ilink.test.ilinkcheck.application.agent.spi.mcp.DevToArticleProvider;
import com.ilink.test.ilinkcheck.application.agent.spi.mcp.GitHubRepoProvider;
import com.ilink.test.ilinkcheck.application.reminder.model.ReminderRecurrence;
import com.ilink.test.ilinkcheck.application.schedule.model.ScheduleIntervalUnit;
import com.ilink.test.ilinkcheck.infrastructure.persistence.digest.InMemoryDigestSubscriptionRepository;
import com.ilink.test.ilinkcheck.infrastructure.scheduler.digest.TechDigestScheduler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.TaskScheduler;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class DailyDigestSubscriptionServiceTest {

    @Test
    void shouldSaveSubscriptionAndScheduleTask() {
        InMemoryDigestSubscriptionRepository repository = new InMemoryDigestSubscriptionRepository();
        DailyDigestComposeService composeService = composeService();
        @SuppressWarnings("unchecked")
        ObjectProvider<WechatBotRuntime> runtimeProvider = mock(ObjectProvider.class);
        TechDigestScheduler scheduler = new TechDigestScheduler(
                repository,
                runtimeProvider,
                new WechatMessageGateway(),
                mock(TaskScheduler.class),
                composeService
        );
        DailyDigestSubscriptionService service = new DailyDigestSubscriptionService(
                repository,
                scheduler,
                Clock.fixed(Instant.parse("2026-07-31T08:00:00Z"), ZoneId.of("UTC")),
                "Asia/Shanghai"
        );

        var subscription = service.subscribe("user-1", "每天 08:30 推送");

        assertThat(subscription.userId()).isEqualTo("user-1");
        assertThat(subscription.scheduleTime()).isEqualTo("每天 08:30");
        assertThat(service.findByUserId("user-1")).isPresent();
    }

    @Test
    void shouldAcceptPlainTimeAsDailyShortcut() {
        InMemoryDigestSubscriptionRepository repository = new InMemoryDigestSubscriptionRepository();
        DailyDigestComposeService composeService = composeService();
        @SuppressWarnings("unchecked")
        ObjectProvider<WechatBotRuntime> runtimeProvider = mock(ObjectProvider.class);
        TechDigestScheduler scheduler = new TechDigestScheduler(
                repository,
                runtimeProvider,
                new WechatMessageGateway(),
                mock(TaskScheduler.class),
                composeService
        );
        DailyDigestSubscriptionService service = new DailyDigestSubscriptionService(
                repository,
                scheduler,
                Clock.fixed(Instant.parse("2026-08-01T08:00:00Z"), ZoneId.of("UTC")),
                "Asia/Shanghai"
        );

        var subscription = service.subscribe("user-1", "08:30");

        assertThat(subscription.scheduleTime()).isEqualTo("每天 08:30");
    }

    @Test
    void shouldAcceptWeeklySchedule() {
        InMemoryDigestSubscriptionRepository repository = new InMemoryDigestSubscriptionRepository();
        DailyDigestComposeService composeService = composeService();
        @SuppressWarnings("unchecked")
        ObjectProvider<WechatBotRuntime> runtimeProvider = mock(ObjectProvider.class);
        TechDigestScheduler scheduler = new TechDigestScheduler(
                repository,
                runtimeProvider,
                new WechatMessageGateway(),
                mock(TaskScheduler.class),
                composeService
        );
        DailyDigestSubscriptionService service = new DailyDigestSubscriptionService(
                repository,
                scheduler,
                Clock.fixed(Instant.parse("2026-08-01T08:00:00Z"), ZoneId.of("UTC")),
                "Asia/Shanghai"
        );

        var subscription = service.subscribe("user-1", "每周二上午9点");

        assertThat(subscription.scheduleTime()).isEqualTo("每周二 09:00");
        assertThat(subscription.recurrence()).isEqualTo(ReminderRecurrence.CRON);
        assertThat(subscription.cronExpression()).isEqualTo("0 0 9 ? * TUE");
    }

    @Test
    void shouldAcceptMonthlySchedule() {
        InMemoryDigestSubscriptionRepository repository = new InMemoryDigestSubscriptionRepository();
        DailyDigestComposeService composeService = composeService();
        @SuppressWarnings("unchecked")
        ObjectProvider<WechatBotRuntime> runtimeProvider = mock(ObjectProvider.class);
        TechDigestScheduler scheduler = new TechDigestScheduler(
                repository,
                runtimeProvider,
                new WechatMessageGateway(),
                mock(TaskScheduler.class),
                composeService
        );
        DailyDigestSubscriptionService service = new DailyDigestSubscriptionService(
                repository,
                scheduler,
                Clock.fixed(Instant.parse("2026-08-01T08:00:00Z"), ZoneId.of("UTC")),
                "Asia/Shanghai"
        );

        var subscription = service.subscribe("user-1", "每月5号晚上8点");

        assertThat(subscription.scheduleTime()).isEqualTo("每月5号 20:00");
        assertThat(subscription.recurrence()).isEqualTo(ReminderRecurrence.CRON);
        assertThat(subscription.cronExpression()).isEqualTo("0 0 20 5 * ?");
    }

    @Test
    void shouldAcceptBiWeeklySchedule() {
        InMemoryDigestSubscriptionRepository repository = new InMemoryDigestSubscriptionRepository();
        DailyDigestComposeService composeService = composeService();
        @SuppressWarnings("unchecked")
        ObjectProvider<WechatBotRuntime> runtimeProvider = mock(ObjectProvider.class);
        TechDigestScheduler scheduler = new TechDigestScheduler(
                repository,
                runtimeProvider,
                new WechatMessageGateway(),
                mock(TaskScheduler.class),
                composeService
        );
        DailyDigestSubscriptionService service = new DailyDigestSubscriptionService(
                repository,
                scheduler,
                Clock.fixed(Instant.parse("2026-08-01T08:00:00Z"), ZoneId.of("UTC")),
                "Asia/Shanghai"
        );

        var subscription = service.subscribe("user-1", "每两周周二上午9点");

        assertThat(subscription.scheduleTime()).isEqualTo("每2周周二 09:00");
        assertThat(subscription.recurrence()).isEqualTo(ReminderRecurrence.INTERVAL);
        assertThat(subscription.intervalValue()).isEqualTo(2);
        assertThat(subscription.intervalUnit()).isEqualTo(ScheduleIntervalUnit.WEEK);
    }

    @Test
    void shouldRejectInvalidInput() {
        InMemoryDigestSubscriptionRepository repository = new InMemoryDigestSubscriptionRepository();
        DailyDigestComposeService composeService = composeService();
        @SuppressWarnings("unchecked")
        ObjectProvider<WechatBotRuntime> runtimeProvider = mock(ObjectProvider.class);
        TechDigestScheduler scheduler = new TechDigestScheduler(
                repository,
                runtimeProvider,
                new WechatMessageGateway(),
                mock(TaskScheduler.class),
                composeService
        );
        DailyDigestSubscriptionService service = new DailyDigestSubscriptionService(
                repository,
                scheduler,
                Clock.systemDefaultZone(),
                "Asia/Shanghai"
        );

        assertThatThrownBy(() -> service.subscribe("", "08:30"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.subscribe("user-1", "早上推送"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.subscribe("user-1", "9点推送"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("上午还是下午");
        assertThatThrownBy(() -> service.subscribe("user-1", "明天上午9点"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("需要重复执行");
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
        };
        DevToArticleProvider devToArticleProvider = (tag, limit) -> List.of(
                new DevToArticle("AI article", "https://dev.to/example/ai", "ai", 100, "article")
        );
        return new DailyDigestComposeService(gitHubRepoProvider, devToArticleProvider, "java", "daily", 3, "ai", 3);
    }
}
