package com.ilink.test.ilinkcheck.infrastructure.scheduler;

import com.github.wechat.ilink.sdk.ILinkClient;
import com.ilink.test.ilinkcheck.adapter.inbound.wechat.WechatBotRuntime;
import com.ilink.test.ilinkcheck.adapter.outbound.wechat.WechatMessageGateway;
import com.ilink.test.ilinkcheck.application.reminder.ReminderService;
import com.ilink.test.ilinkcheck.application.reminder.model.ReminderRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
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
    private final Clock clock;

    public ReminderScheduler(ReminderService reminderService,
                             WechatBotRuntime wechatBotRuntime,
                             WechatMessageGateway wechatMessageGateway,
                             Clock clock) {
        this.reminderService = reminderService;
        this.wechatBotRuntime = wechatBotRuntime;
        this.wechatMessageGateway = wechatMessageGateway;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.reminder.dispatch-interval-ms:10000}")
    public void dispatchDueReminders() {
        Optional<ILinkClient> client = wechatBotRuntime.currentClient();
        if (client.isEmpty()) {
            return;
        }

        Instant now = clock.instant();
        for (ReminderRecord reminder : reminderService.findDueReminders(now)) {
            try {
                boolean sent = wechatMessageGateway.sendTextIfPossible(
                        client.get(),
                        reminder.userId(),
                        reminderService.buildFireMessage(reminder)
                );
                if (sent) {
                    reminderService.acknowledgeFired(reminder, now);
                }
            }
            catch (Exception e) {
                log.warn("Failed to dispatch reminder {} for user {}", reminder.id(), reminder.userId(), e);
            }
        }
    }
}
