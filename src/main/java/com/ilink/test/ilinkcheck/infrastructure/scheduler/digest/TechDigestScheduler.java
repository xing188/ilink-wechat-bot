package com.ilink.test.ilinkcheck.infrastructure.scheduler.digest;

import com.github.wechat.ilink.sdk.ILinkClient;
import com.ilink.test.ilinkcheck.adapter.inbound.wechat.WechatBotRuntime;
import com.ilink.test.ilinkcheck.adapter.outbound.wechat.WechatMessageGateway;
import com.ilink.test.ilinkcheck.application.agent.dto.digest.DigestContent;
import com.ilink.test.ilinkcheck.application.agent.dto.digest.DigestSubscription;
import com.ilink.test.ilinkcheck.application.agent.feature.digest.DailyDigestComposeService;
import com.ilink.test.ilinkcheck.application.agent.feature.digest.DigestSubscriptionRepository;
import com.ilink.test.ilinkcheck.application.reminder.model.ReminderRecurrence;
import com.ilink.test.ilinkcheck.application.schedule.ScheduleNextFireCalculator;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * 技术晨报调度器。
 * 现在不再假定“只支持每天 HH:mm”，而是复用通用定时模型：
 * CRON 类规则交给 Spring 自己循环；间隔型规则在触发后续排下一次。
 */
@Service
@Slf4j
public class TechDigestScheduler {

    private final DigestSubscriptionRepository repository;
    private final ObjectProvider<WechatBotRuntime> wechatBotRuntimeProvider;
    private final WechatMessageGateway wechatMessageGateway;
    private final TaskScheduler taskScheduler;
    private final DailyDigestComposeService dailyDigestComposeService;
    private final Map<String, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    public TechDigestScheduler(DigestSubscriptionRepository repository,
                               ObjectProvider<WechatBotRuntime> wechatBotRuntimeProvider,
                               WechatMessageGateway wechatMessageGateway,
                               TaskScheduler taskScheduler,
                               DailyDigestComposeService dailyDigestComposeService) {
        this.repository = repository;
        this.wechatBotRuntimeProvider = wechatBotRuntimeProvider;
        this.wechatMessageGateway = wechatMessageGateway;
        this.taskScheduler = taskScheduler;
        this.dailyDigestComposeService = dailyDigestComposeService;
    }

    @PostConstruct
    public void reloadSubscriptions() {
        repository.findAll().forEach(this::scheduleSubscription);
    }

    /**
     * 注册或更新一个用户的晨报任务。
     */
    public void scheduleSubscription(DigestSubscription subscription) {
        cancelSubscription(subscription.userId());

        ScheduledFuture<?> future = schedule(subscription, () -> fireSubscription(subscription.userId()));
        if (future != null) {
            scheduledTasks.put(subscription.userId(), future);
            log.info("Digest scheduled. userId={}, plan={}, recurrence={}, nextFireAt={}, cron={}, zone={}",
                    subscription.userId(),
                    subscription.scheduleTime(),
                    subscription.recurrence(),
                    subscription.nextFireAt(),
                    subscription.cronExpression(),
                    subscription.zoneId());
        }
    }

    public void cancelSubscription(String userId) {
        ScheduledFuture<?> future = scheduledTasks.remove(userId);
        if (future != null && !future.isCancelled()) {
            future.cancel(false);
            log.info("Digest canceled. userId={}", userId);
        }
    }

    public boolean isScheduled(String userId) {
        ScheduledFuture<?> future = scheduledTasks.get(userId);
        return future != null && !future.isCancelled();
    }

    /**
     * 供测试或运维手动触发一次推送。
     * 手动触发只发一次，不推进订阅计划。
     */
    public boolean triggerNow(String userId) {
        Optional<DigestSubscription> subscription = repository.findByUserId(userId);
        if (subscription.isEmpty()) {
            return false;
        }
        fireSubscription(subscription.get(), false);
        return true;
    }

    void fireSubscription(String userId) {
        repository.findByUserId(userId).ifPresent(subscription -> fireSubscription(subscription, true));
    }

    private ScheduledFuture<?> schedule(DigestSubscription subscription, Runnable task) {
        if (subscription.recurrence() == ReminderRecurrence.CRON
                && StringUtils.hasText(subscription.cronExpression())) {
            return taskScheduler.schedule(
                    task,
                    new CronTrigger(subscription.cronExpression(), ZoneId.of(subscription.zoneId()))
            );
        }
        if (subscription.nextFireAt() != null) {
            return taskScheduler.schedule(task, subscription.nextFireAt());
        }
        return null;
    }

    private void fireSubscription(DigestSubscription subscription, boolean advanceSchedule) {
        WechatBotRuntime runtime = wechatBotRuntimeProvider.getIfAvailable();
        if (runtime == null) {
            log.warn("WechatBotRuntime not available, skip digest push. userId={}", subscription.userId());
            return;
        }

        Optional<ILinkClient> client = runtime.currentClient();
        if (client.isEmpty()) {
            log.warn("Wechat client not ready, skip digest push. userId={}", subscription.userId());
            return;
        }

        DigestContent digestContent = dailyDigestComposeService.collect();
        String message = dailyDigestComposeService.buildPushMessage(digestContent);
        boolean sent = wechatMessageGateway.sendTextIfPossible(client.get(), subscription.userId(), message);
        if (!sent) {
            return;
        }

        if (advanceSchedule) {
            advanceSubscription(subscription, Instant.now()).ifPresent(updated -> {
                repository.save(updated);
                if (updated.recurrence() != ReminderRecurrence.CRON) {
                    scheduleSubscription(updated);
                }
            });
        }

        log.info("Digest pushed. userId={}, plan={}", subscription.userId(), subscription.scheduleTime());
    }

    private Optional<DigestSubscription> advanceSubscription(DigestSubscription subscription, Instant firedAt) {
        return ScheduleNextFireCalculator.nextFireAt(
                        subscription.recurrence(),
                        subscription.nextFireAt(),
                        firedAt,
                        subscription.zoneId(),
                        subscription.cronExpression(),
                        subscription.intervalValue(),
                        subscription.intervalUnit()
                )
                .map(nextFireAt -> new DigestSubscription(
                        subscription.userId(),
                        subscription.scheduleTime(),
                        subscription.zoneId(),
                        subscription.recurrence(),
                        nextFireAt,
                        subscription.cronExpression(),
                        subscription.intervalValue(),
                        subscription.intervalUnit(),
                        subscription.createdAt(),
                        firedAt
                ));
    }

    @PreDestroy
    public void shutdown() {
        for (ScheduledFuture<?> future : scheduledTasks.values()) {
            if (future != null && !future.isCancelled()) {
                future.cancel(false);
            }
        }
        scheduledTasks.clear();
    }
}
