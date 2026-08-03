package com.ilink.test.ilinkcheck.application.reminder.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ilink.test.ilinkcheck.application.schedule.model.ScheduleIntervalUnit;

import java.time.Instant;
@JsonIgnoreProperties(ignoreUnknown = true)
public record ReminderRecord(
        String id,                     // 唯一主键
        String userId,                 // 归属用户微信ID
        String content,                // 提醒文本内容
        String originalRequest,        // 用户原始自然语言指令
        String scheduleLabel,          // 提醒标题/标签
        ReminderRecurrence recurrence, // 重复规则枚举：一次性/每天/CRON
        ReminderAction action,         // 触发动作：NONE/FETCH_NEWS
        ReminderStatus status,         // 状态枚举：ACTIVE / COMPLETED / CANCEL
        String timezone,               // 时区
        Instant nextFireAt,            // 下一次触发时间
        String cronExpression,         // CRON 表达式（仅 recurrence=CRON 时使用）
        Integer intervalValue,         // 间隔型规则的步长，例如“每2周”里的 2
        ScheduleIntervalUnit intervalUnit, // 间隔型规则的单位，例如 WEEK / MONTH
        Instant createdAt,
        Instant updatedAt,
        Instant completedAt,           // 全部完成时间，循环提醒一般为空
        Instant lastTriggeredAt        // 上一次推送触发时间
) {
    public boolean isActive() {
        return status == ReminderStatus.ACTIVE;
    }
}
