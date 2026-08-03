package com.ilink.test.ilinkcheck.application.schedule.model;

import java.time.ZonedDateTime;

/**
 * 间隔型定时的时间单位。
 * 之所以单独抽出来，是为了让“每两周”“每三个月”这类规则
 * 能够在提醒和晨报订阅之间共享同一套计算逻辑。
 */
public enum ScheduleIntervalUnit {
    SECOND,
    MINUTE,
    HOUR,
    DAY,
    WEEK,
    MONTH;

    public ZonedDateTime addTo(ZonedDateTime base, int amount) {
        return switch (this) {
            case SECOND -> base.plusSeconds(amount);
            case MINUTE -> base.plusMinutes(amount);
            case HOUR -> base.plusHours(amount);
            case DAY -> base.plusDays(amount);
            case WEEK -> base.plusWeeks(amount);
            case MONTH -> base.plusMonths(amount);
        };
    }
}
