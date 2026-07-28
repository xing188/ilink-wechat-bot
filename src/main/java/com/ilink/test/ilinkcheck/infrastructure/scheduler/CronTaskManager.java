package com.ilink.test.ilinkcheck.infrastructure.scheduler;

import com.ilink.test.ilinkcheck.application.reminder.model.ReminderRecord;
import com.ilink.test.ilinkcheck.application.reminder.model.ReminderRecurrence;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

@Component
@Slf4j
public class CronTaskManager {

    private final TaskScheduler taskScheduler;
    private final Map<String, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    public CronTaskManager(TaskScheduler taskScheduler) {
        this.taskScheduler = taskScheduler;
    }

    public void scheduleReminder(ReminderRecord reminder, Runnable task) {
        cancelReminder(reminder.id());

        ScheduledFuture<?> future;
        if (reminder.recurrence() == ReminderRecurrence.CRON
                && reminder.cronExpression() != null
                && !reminder.cronExpression().isBlank()) {
            future = taskScheduler.schedule(task, new CronTrigger(reminder.cronExpression()));
            log.info("CRON 任务已调度: id={}, cron={}", reminder.id(), reminder.cronExpression());
        }
        else {
            future = taskScheduler.schedule(task, reminder.nextFireAt());
            log.info("定时任务已调度: id={}, fireAt={}", reminder.id(), reminder.nextFireAt());
        }

        scheduledTasks.put(reminder.id(), future);
    }

    public void cancelReminder(String reminderId) {
        ScheduledFuture<?> future = scheduledTasks.remove(reminderId);
        if (future != null && !future.isCancelled()) {
            future.cancel(false);
            log.info("任务已取消: id={}", reminderId);
        }
    }

    public boolean isScheduled(String reminderId) {
        ScheduledFuture<?> future = scheduledTasks.get(reminderId);
        return future != null && !future.isCancelled();
    }

    @PreDestroy
    public void shutdown() {
        log.info("正在优雅关闭定时任务管理器，剩余任务数: {}", scheduledTasks.size());
        for (Map.Entry<String, ScheduledFuture<?>> entry : scheduledTasks.entrySet()) {
            ScheduledFuture<?> future = entry.getValue();
            if (future != null && !future.isCancelled()) {
                future.cancel(false);
                log.debug("任务已取消: id={}", entry.getKey());
            }
        }
        scheduledTasks.clear();
        log.info("定时任务管理器已关闭");
    }
}
