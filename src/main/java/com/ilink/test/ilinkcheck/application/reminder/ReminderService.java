package com.ilink.test.ilinkcheck.application.reminder;

import com.ilink.test.ilinkcheck.application.reminder.model.ReminderRaw;
import com.ilink.test.ilinkcheck.application.reminder.model.ReminderRecord;
import com.ilink.test.ilinkcheck.application.reminder.model.ReminderRecurrence;
import com.ilink.test.ilinkcheck.application.reminder.model.ReminderStatus;
import com.ilink.test.ilinkcheck.application.reminder.parser.ReminderParseException;
import com.ilink.test.ilinkcheck.application.reminder.parser.ReminderParser;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
public class ReminderService {

    private static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final int DUE_BATCH_SIZE = 20;
    private static final String COMPLETED_STATUS_SUFFIX = "（已完成）";
    private static final String CANCELED_STATUS_SUFFIX = "（已取消）";

    private final ReminderRepository reminderRepository;
    private final ReminderParser reminderParser;
    private final Clock clock;

    public ReminderService(ReminderRepository reminderRepository,
                           ReminderParser reminderParser,
                           Clock clock) {
        this.reminderRepository = reminderRepository;
        this.reminderParser = reminderParser;
        this.clock = clock;
    }

    public String createReminder(String userId, String request) {
        String normalizedUserId = normalizeUserId(userId);
        if (!StringUtils.hasText(normalizedUserId)) {
            return "无法识别当前用户，提醒未创建。";
        }

        try {
            ReminderRaw reminder = reminderParser.parse(request, clock);
            Instant now = clock.instant();
            ReminderRecord record = new ReminderRecord(
                    UUID.randomUUID().toString().replace("-", ""),
                    normalizedUserId,
                    reminder.content(),
                    request,
                    reminder.scheduleLabel(),
                    reminder.recurrence(),
                    ReminderStatus.ACTIVE,
                    clock.getZone().getId(),
                    reminder.nextFireAt(),
                    now,
                    now,
                    null,
                    null
            );
            reminderRepository.save(record);
            return formatCreateSuccess(record);
        }
        catch (ReminderParseException e) {
            return e.getMessage();
        }
        catch (Exception e) {
            return "提醒创建失败，请稍后再试。";
        }
    }

    public String listPendingReminders(String userId) {
        String normalizedUserId = normalizeUserId(userId);
        if (!StringUtils.hasText(normalizedUserId)) {
            return "无法识别当前用户，暂时不能查看待办。";
        }

        List<ReminderRecord> reminders = reminderRepository.findPendingByUser(normalizedUserId);
        if (reminders.isEmpty()) {
            return "当前没有待办提醒。";
        }

        StringBuilder sb = new StringBuilder("当前待办提醒：\n");
        int index = 1;
        for (ReminderRecord reminder : reminders) {
            sb.append(index++)
                    .append(". ")
                    .append(formatReminderLine(reminder))
                    .append('\n');
        }
        return sb.toString().trim();
    }

    public List<ReminderRecord> findDueReminders(Instant now) {
        return reminderRepository.findDueReminders(now, DUE_BATCH_SIZE);
    }

    public ReminderRecord acknowledgeFired(ReminderRecord reminder, Instant firedAt) {
        if (reminder == null || !reminder.isActive()) {
            return reminder;
        }

        ReminderRecord updated;
        if (reminder.recurrence() == ReminderRecurrence.DAILY) {
            Instant nextFireAt = computeNextDailyFireAt(reminder, firedAt);
            updated = new ReminderRecord(
                    reminder.id(),
                    reminder.userId(),
                    reminder.content(),
                    reminder.originalRequest(),
                    reminder.scheduleLabel(),
                    reminder.recurrence(),
                    ReminderStatus.ACTIVE,
                    reminder.timezone(),
                    nextFireAt,
                    reminder.createdAt(),
                    firedAt,
                    null,
                    firedAt
            );
        }
        else {
            updated = new ReminderRecord(
                    reminder.id(),
                    reminder.userId(),
                    reminder.content(),
                    reminder.originalRequest(),
                    reminder.scheduleLabel(),
                    reminder.recurrence(),
                    ReminderStatus.COMPLETED,
                    reminder.timezone(),
                    reminder.nextFireAt(),
                    reminder.createdAt(),
                    firedAt,
                    firedAt,
                    firedAt
            );
        }

        return reminderRepository.save(updated);
    }

    public String buildFireMessage(ReminderRecord reminder) {
        if (reminder.recurrence() == ReminderRecurrence.DAILY) {
            return "⏰ 每日提醒：" + reminder.content();
        }
        return "⏰ 提醒：" + reminder.content();
    }

    private String formatCreateSuccess(ReminderRecord record) {
        String prefix = record.recurrence() == ReminderRecurrence.DAILY ? "已添加每日提醒：" : "已添加提醒：";
        return prefix + record.scheduleLabel() + " " + record.content();
    }

    private String formatReminderLine(ReminderRecord reminder) {
        String statusSuffix = switch (reminder.status()) {
            case COMPLETED -> COMPLETED_STATUS_SUFFIX;
            case CANCELED -> CANCELED_STATUS_SUFFIX;
            case ACTIVE -> "";
        };
        return "[" + reminder.scheduleLabel() + "] " + reminder.content() + statusSuffix;
    }

    private String normalizeUserId(String userId) {
        return StringUtils.hasText(userId) ? userId.trim() : null;
    }

    private Instant computeNextDailyFireAt(ReminderRecord reminder, Instant firedAt) {
        ZoneId zoneId = ZoneId.of(reminder.timezone());
        ZonedDateTime scheduledTime = ZonedDateTime.ofInstant(reminder.nextFireAt(), zoneId);
        ZonedDateTime now = ZonedDateTime.ofInstant(firedAt, zoneId);
        ZonedDateTime candidate = now.withHour(scheduledTime.getHour())
                .withMinute(scheduledTime.getMinute())
                .withSecond(scheduledTime.getSecond())
                .withNano(scheduledTime.getNano());
        if (!candidate.isAfter(now)) {
            candidate = candidate.plusDays(1);
        }
        return candidate.toInstant();
    }
}
