package com.ilink.test.ilinkcheck.application.schedule.model;

import com.ilink.test.ilinkcheck.application.reminder.model.ReminderRecurrence;

import java.time.Instant;

/**
 * 统一的定时计划模型。
 * 提醒、晨报、后续周报都可以只关心“执行什么”，
 * 这里负责表达“什么时候执行”和“如何重复执行”。
 */
public record SchedulePlan(
        String scheduleLabel,
        ReminderRecurrence recurrence,
        Instant nextFireAt,
        String cronExpression,
        Integer intervalValue,
        ScheduleIntervalUnit intervalUnit
) {
}
