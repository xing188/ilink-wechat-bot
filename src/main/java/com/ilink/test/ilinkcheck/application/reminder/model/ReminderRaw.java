package com.ilink.test.ilinkcheck.application.reminder.model;

import java.time.Instant;

public record ReminderRaw(
        String content,
        String scheduleLabel,
        ReminderRecurrence recurrence,
        Instant nextFireAt
) {
}
