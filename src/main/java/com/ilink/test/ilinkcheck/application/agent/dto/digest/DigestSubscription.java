package com.ilink.test.ilinkcheck.application.agent.dto.digest;

import com.ilink.test.ilinkcheck.application.reminder.model.ReminderRecurrence;
import com.ilink.test.ilinkcheck.application.schedule.model.ScheduleIntervalUnit;

import java.time.Instant;

/**
 * 晨报订阅记录。
 * 这里不再假设一定是“每天 HH:mm”，而是复用通用定时模型：
 * 可以表达每天、每周、每月，以及间隔型计划。
 */
public record DigestSubscription(
        String userId,
        String scheduleTime,
        String zoneId,
        ReminderRecurrence recurrence,
        Instant nextFireAt,
        String cronExpression,
        Integer intervalValue,
        ScheduleIntervalUnit intervalUnit,
        Instant createdAt,
        Instant updatedAt
) {

    /**
     * 兼容旧的“每天 HH:mm”测试和构造点。
     */
    public DigestSubscription(String userId,
                              String scheduleTime,
                              String zoneId,
                              Instant createdAt,
                              Instant updatedAt) {
        this(
                userId,
                scheduleTime,
                zoneId,
                ReminderRecurrence.DAILY,
                null,
                null,
                null,
                null,
                createdAt,
                updatedAt
        );
    }
}
