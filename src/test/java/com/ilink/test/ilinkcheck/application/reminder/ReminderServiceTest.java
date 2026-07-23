package com.ilink.test.ilinkcheck.application.reminder;

import com.ilink.test.ilinkcheck.application.reminder.model.ReminderRecord;
import com.ilink.test.ilinkcheck.application.reminder.model.ReminderRecurrence;
import com.ilink.test.ilinkcheck.application.reminder.model.ReminderStatus;
import com.ilink.test.ilinkcheck.application.reminder.parser.ReminderParser;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReminderServiceTest {

    private final ZoneId zoneId = ZoneId.of("Asia/Shanghai");

    @Test
    void shouldCreateAndPersistReminder() {
        ReminderRepository repository = mock(ReminderRepository.class);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ReminderService service = new ReminderService(repository, new ReminderParser(), fixedClock(2026, 7, 23, 11, 0));

        String result = service.createReminder("user-1", "今天下午5点给张三发个微信消息");

        ArgumentCaptor<ReminderRecord> captor = ArgumentCaptor.forClass(ReminderRecord.class);
        verify(repository).save(captor.capture());
        ReminderRecord saved = captor.getValue();

        assertThat(saved.userId()).isEqualTo("user-1");
        assertThat(saved.content()).isEqualTo("给张三发个微信消息");
        assertThat(saved.recurrence()).isEqualTo(ReminderRecurrence.ONCE);
        assertThat(result).contains("已添加提醒：今天 17:00 给张三发个微信消息");
    }

    @Test
    void shouldRescheduleDailyReminderAfterFire() {
        ReminderRepository repository = mock(ReminderRepository.class);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ReminderService service = new ReminderService(repository, new ReminderParser(), fixedClock(2026, 7, 23, 7, 0));
        ReminderRecord reminder = new ReminderRecord(
                "id-1",
                "user-1",
                "推送科技新闻",
                "每天早上8点自动给我推送科技新闻",
                "每天 08:00",
                ReminderRecurrence.DAILY,
                ReminderStatus.ACTIVE,
                zoneId.getId(),
                instant(2026, 7, 23, 8, 0),
                instant(2026, 7, 23, 7, 0),
                instant(2026, 7, 23, 7, 0),
                null,
                null
        );

        ReminderRecord updated = service.acknowledgeFired(reminder, instant(2026, 7, 23, 8, 5));

        assertThat(updated.status()).isEqualTo(ReminderStatus.ACTIVE);
        assertThat(updated.nextFireAt()).isEqualTo(instant(2026, 7, 24, 8, 0));
        verify(repository).save(any(ReminderRecord.class));
    }

    @Test
    void shouldListPendingReminders() {
        ReminderRepository repository = mock(ReminderRepository.class);
        ReminderRecord reminder = new ReminderRecord(
                "id-2",
                "user-1",
                "开会",
                "十分钟后开会",
                "10分钟后",
                ReminderRecurrence.ONCE,
                ReminderStatus.ACTIVE,
                zoneId.getId(),
                instant(2026, 7, 23, 11, 10),
                instant(2026, 7, 23, 11, 0),
                instant(2026, 7, 23, 11, 0),
                null,
                null
        );
        when(repository.findPendingByUser("user-1")).thenReturn(List.of(reminder));

        ReminderService service = new ReminderService(repository, new ReminderParser(), fixedClock(2026, 7, 23, 11, 0));

        String result = service.listPendingReminders("user-1");

        assertThat(result).contains("当前待办提醒");
        assertThat(result).contains("[10分钟后] 开会");
    }

    private Clock fixedClock(int year, int month, int day, int hour, int minute) {
        return Clock.fixed(ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zoneId).toInstant(), zoneId);
    }

    private Instant instant(int year, int month, int day, int hour, int minute) {
        return ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zoneId).toInstant();
    }
}
