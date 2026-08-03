package com.ilink.test.ilinkcheck.application.schedule;

import com.ilink.test.ilinkcheck.application.reminder.model.ReminderRecurrence;
import com.ilink.test.ilinkcheck.application.schedule.model.ScheduleIntervalUnit;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;

/**
 * 统一计算“下一次触发时间”。
 * 这样提醒和晨报都不需要各自维护一套重复计算逻辑。
 */
public final class ScheduleNextFireCalculator {

    private ScheduleNextFireCalculator() {
    }

    public static Optional<Instant> nextFireAt(ReminderRecurrence recurrence,
                                               Instant scheduledAt,
                                               Instant referenceTime,
                                               String zoneId,
                                               String cronExpression,
                                               Integer intervalValue,
                                               ScheduleIntervalUnit intervalUnit) {
        if (recurrence == null || referenceTime == null || !StringUtils.hasText(zoneId)) {
            return Optional.empty();
        }

        ZoneId actualZoneId = ZoneId.of(zoneId);
        Instant actualScheduledAt = scheduledAt != null ? scheduledAt : referenceTime;
        ZonedDateTime reference = ZonedDateTime.ofInstant(referenceTime, actualZoneId);
        ZonedDateTime scheduled = ZonedDateTime.ofInstant(actualScheduledAt, actualZoneId);

        return switch (recurrence) {
            case ONCE -> Optional.empty();
            case DAILY -> Optional.of(nextDailyFireAt(scheduled, reference).toInstant());
            case CRON -> nextCronFireAt(reference, cronExpression);
            case INTERVAL -> nextIntervalFireAt(scheduled, reference, intervalValue, intervalUnit);
        };
    }

    private static ZonedDateTime nextDailyFireAt(ZonedDateTime scheduled, ZonedDateTime reference) {
        ZonedDateTime candidate = reference.withHour(scheduled.getHour())
                .withMinute(scheduled.getMinute())
                .withSecond(scheduled.getSecond())
                .withNano(scheduled.getNano());
        if (!candidate.isAfter(reference)) {
            candidate = candidate.plusDays(1);
        }
        return candidate;
    }

    private static Optional<Instant> nextCronFireAt(ZonedDateTime reference, String cronExpression) {
        if (!StringUtils.hasText(cronExpression)) {
            return Optional.empty();
        }
        ZonedDateTime next = CronExpression.parse(cronExpression).next(reference);
        return next == null ? Optional.empty() : Optional.of(next.toInstant());
    }

    private static Optional<Instant> nextIntervalFireAt(ZonedDateTime scheduled,
                                                        ZonedDateTime reference,
                                                        Integer intervalValue,
                                                        ScheduleIntervalUnit intervalUnit) {
        if (intervalValue == null || intervalValue <= 0 || intervalUnit == null) {
            return Optional.empty();
        }

        ZonedDateTime candidate = scheduled;
        while (!candidate.isAfter(reference)) {
            candidate = intervalUnit.addTo(candidate, intervalValue);
        }
        return Optional.of(candidate.toInstant());
    }
}
