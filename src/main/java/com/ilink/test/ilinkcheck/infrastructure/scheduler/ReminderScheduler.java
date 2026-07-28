package com.ilink.test.ilinkcheck.infrastructure.scheduler;

import com.github.wechat.ilink.sdk.ILinkClient;
import com.ilink.test.ilinkcheck.adapter.inbound.wechat.WechatBotRuntime;
import com.ilink.test.ilinkcheck.adapter.outbound.wechat.WechatMessageGateway;
import com.ilink.test.ilinkcheck.application.reminder.ReminderService;
import com.ilink.test.ilinkcheck.application.reminder.model.ReminderRecord;
import com.ilink.test.ilinkcheck.application.reminder.model.ReminderRecurrence;
import com.ilink.test.ilinkcheck.application.reminder.model.ReminderStatus;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

@Service
@Slf4j
@ConditionalOnProperty(prefix = "app.ilink", name = "enabled", havingValue = "true")
public class ReminderScheduler {

    private final ReminderService reminderService;
    private final WechatBotRuntime wechatBotRuntime;
    private final WechatMessageGateway wechatMessageGateway;
    private final CronTaskManager cronTaskManager;
    private final Clock clock;

    public ReminderScheduler(ReminderService reminderService,
                             WechatBotRuntime wechatBotRuntime,
                             WechatMessageGateway wechatMessageGateway,
                             CronTaskManager cronTaskManager,
                             Clock clock) {
        this.reminderService = reminderService;
        this.wechatBotRuntime = wechatBotRuntime;
        this.wechatMessageGateway = wechatMessageGateway;
        this.cronTaskManager = cronTaskManager;
        this.clock = clock;
    }

    @PostConstruct
    public void reloadActiveReminders() {
        log.info("正在重新加载活动提醒任务...");
        reminderService.findActiveReminders()
                .forEach(this::scheduleReminder);
    }

    public void scheduleReminder(ReminderRecord reminder) {
        if (!reminder.isActive()) {
            return;
        }

        Runnable task = () -> fireReminder(reminder);
        cronTaskManager.scheduleReminder(reminder, task);
    }

    public void cancelReminder(String reminderId) {
        cronTaskManager.cancelReminder(reminderId);
    }

    private void fireReminder(ReminderRecord reminder) {
        Optional<ILinkClient> client = wechatBotRuntime.currentClient();
        if (client.isEmpty()) {
            log.warn("微信客户端未就绪，无法推送提醒: id={}", reminder.id());
            return;
        }

        try {
            Instant now = clock.instant();
            boolean sent = wechatMessageGateway.sendTextIfPossible(
                    client.get(),
                    reminder.userId(),
                    reminderService.buildFireMessage(reminder)
            );

            if (sent) {
                ReminderRecord updated = reminderService.acknowledgeFired(reminder, now);
                if (updated.status() == ReminderStatus.ACTIVE) {
                    scheduleReminder(updated);
                }
                log.info("提醒已推送: id={}, userId={}", reminder.id(), reminder.userId());
            }
        }
        catch (Exception e) {
            log.warn("提醒推送失败: id={}, userId={}", reminder.id(), reminder.userId(), e);
        }
    }
}
