package com.ilink.test.ilinkcheck.application.reminder.model;

import com.ilink.test.ilinkcheck.application.schedule.model.ScheduleIntervalUnit;

import java.time.Instant;

public record ReminderRaw(
        String content,
        String scheduleLabel,
        ReminderRecurrence recurrence,
        Instant nextFireAt,
        String cronExpression,
        Integer intervalValue,
        ScheduleIntervalUnit intervalUnit,
        ReminderAction action
) {
}
