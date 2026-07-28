package com.ilink.test.ilinkcheck.application.reminder;

import com.ilink.test.ilinkcheck.application.reminder.model.ReminderAction;
import com.ilink.test.ilinkcheck.application.reminder.model.ReminderRaw;
import com.ilink.test.ilinkcheck.application.reminder.model.ReminderRecord;
import com.ilink.test.ilinkcheck.application.reminder.model.ReminderRecurrence;
import com.ilink.test.ilinkcheck.application.reminder.model.ReminderStatus;
import com.ilink.test.ilinkcheck.application.reminder.parser.ReminderParseException;
import com.ilink.test.ilinkcheck.application.reminder.parser.ReminderParser;
import com.ilink.test.ilinkcheck.infrastructure.scheduler.ReminderScheduler;
import org.springframework.context.annotation.Lazy;
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
    private final ReminderScheduler reminderScheduler;
    private final Clock clock;

    public ReminderService(ReminderRepository reminderRepository,
                           ReminderParser reminderParser,
                           @Lazy ReminderScheduler reminderScheduler,
                           Clock clock) {
        this.reminderRepository = reminderRepository;
        this.reminderParser = reminderParser;
        this.reminderScheduler = reminderScheduler;
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
                    reminder.action(),
                    ReminderStatus.ACTIVE,
                    clock.getZone().getId(),
                    reminder.nextFireAt(),
                    reminder.cronExpression(),
                    now,
                    now,
                    null,
                    null
            );
            reminderRepository.save(record);
            reminderScheduler.scheduleReminder(record);
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

    public List<ReminderRecord> findActiveReminders() {
        return reminderRepository.findAllActive();
    }

    public String cancelReminder(String userId, String reminderId) {
        if (!StringUtils.hasText(reminderId)) {
            return "请提供要取消的提醒编号。";
        }

        String actualId = reminderId.trim();
        List<ReminderRecord> activeReminders = reminderRepository.findPendingByUser(userId);

        // 支持数字序号：如 "1" 表示第一条
        if (actualId.matches("\\d+")) {
            int index = Integer.parseInt(actualId);
            if (index < 1 || index > activeReminders.size()) {
                return "编号 " + index + " 超出范围，当前有 " + activeReminders.size() + " 条待办提醒。";
            }
            actualId = activeReminders.get(index - 1).id();
        }

        var opt = reminderRepository.findById(actualId);
        if (opt.isEmpty()) {
            return "未找到该提醒，请先查看待办事项确认编号。";
        }

        ReminderRecord reminder = opt.get();
        if (!reminder.userId().equals(userId)) {
            return "这不是您的提醒，无法取消。";
        }
        if (reminder.status() != ReminderStatus.ACTIVE) {
            return "该提醒已处于" + (reminder.status() == ReminderStatus.COMPLETED ? "已完成" : "已取消") + "状态。";
        }

        reminderScheduler.cancelReminder(actualId);
        ReminderRecord updated = new ReminderRecord(
                reminder.id(), reminder.userId(), reminder.content(), reminder.originalRequest(),
                reminder.scheduleLabel(), reminder.recurrence(), reminder.action(), ReminderStatus.CANCELED,
                reminder.timezone(), reminder.nextFireAt(), reminder.cronExpression(),
                reminder.createdAt(), clock.instant(), null, reminder.lastTriggeredAt()
        );
        reminderRepository.save(updated);
        return "✅ 已取消提醒：" + reminder.scheduleLabel() + " " + reminder.content();
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
                    ReminderRecurrence.DAILY,
                    reminder.action(),
                    ReminderStatus.ACTIVE,
                    reminder.timezone(),
                    nextFireAt,
                    null,
                    reminder.createdAt(),
                    firedAt,
                    null,
                    firedAt
            );
        }
        else if (reminder.recurrence() == ReminderRecurrence.CRON) {
            updated = new ReminderRecord(
                    reminder.id(),
                    reminder.userId(),
                    reminder.content(),
                    reminder.originalRequest(),
                    reminder.scheduleLabel(),
                    ReminderRecurrence.CRON,
                    reminder.action(),
                    ReminderStatus.ACTIVE,
                    reminder.timezone(),
                    reminder.nextFireAt(),
                    reminder.cronExpression(),
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
                    ReminderRecurrence.ONCE,
                    reminder.action(),
                    ReminderStatus.COMPLETED,
                    reminder.timezone(),
                    reminder.nextFireAt(),
                    null,
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
