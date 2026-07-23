package com.ilink.test.ilinkcheck.application.reminder;

import com.ilink.test.ilinkcheck.application.reminder.model.ReminderRaw;
import com.ilink.test.ilinkcheck.application.reminder.model.ReminderRecurrence;
import com.ilink.test.ilinkcheck.application.reminder.parser.ReminderParser;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ReminderParserTest {

    private final ReminderParser parser = new ReminderParser();
    private final ZoneId zoneId = ZoneId.of("Asia/Shanghai");

    @Test
    void shouldParseRelativeReminder() {
        Clock clock = fixedClock(2026, 7, 23, 11, 0);

        ReminderRaw spec = parser.parse("提醒我十分钟后开会", clock);

        assertThat(spec.recurrence()).isEqualTo(ReminderRecurrence.ONCE);
        assertThat(spec.content()).isEqualTo("开会");
        assertThat(spec.scheduleLabel()).isEqualTo("10分钟后");
        assertThat(spec.nextFireAt()).isEqualTo(instant(2026, 7, 23, 11, 10));
    }

    @Test
    void shouldParseAbsoluteReminder() {
        Clock clock = fixedClock(2026, 7, 23, 11, 0);

        ReminderRaw spec = parser.parse("今天下午5点给张三发个微信消息", clock);

        assertThat(spec.recurrence()).isEqualTo(ReminderRecurrence.ONCE);
        assertThat(spec.content()).isEqualTo("给张三发个微信消息");
        assertThat(spec.scheduleLabel()).isEqualTo("今天 17:00");
        assertThat(spec.nextFireAt()).isEqualTo(instant(2026, 7, 23, 17, 0));
    }

    @Test
    void shouldParseDailyReminder() {
        Clock clock = fixedClock(2026, 7, 23, 7, 0);

        ReminderRaw spec = parser.parse("每天早上8点自动给我推送科技新闻", clock);

        assertThat(spec.recurrence()).isEqualTo(ReminderRecurrence.DAILY);
        assertThat(spec.content()).isEqualTo("推送科技新闻");
        assertThat(spec.scheduleLabel()).isEqualTo("每天 08:00");
        assertThat(spec.nextFireAt()).isEqualTo(instant(2026, 7, 23, 8, 0));
    }

    private Clock fixedClock(int year, int month, int day, int hour, int minute) {
        return Clock.fixed(ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zoneId).toInstant(), zoneId);
    }

    private Instant instant(int year, int month, int day, int hour, int minute) {
        return ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zoneId).toInstant();
    }
}
