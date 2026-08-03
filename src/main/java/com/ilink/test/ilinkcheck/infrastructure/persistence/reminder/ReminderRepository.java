package com.ilink.test.ilinkcheck.infrastructure.persistence.reminder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ilink.test.ilinkcheck.application.reminder.model.ReminderAction;
import com.ilink.test.ilinkcheck.application.reminder.model.ReminderRecord;
import com.ilink.test.ilinkcheck.application.reminder.model.ReminderRecurrence;
import com.ilink.test.ilinkcheck.application.reminder.model.ReminderStatus;
import com.ilink.test.ilinkcheck.application.schedule.model.ScheduleIntervalUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
@Slf4j
public class ReminderRepository implements com.ilink.test.ilinkcheck.application.reminder.ReminderRepository {

    private static final String REMINDER_KEY_PREFIX = "reminder:record:";
    private static final String REMINDER_DUE_KEY = "reminder:due";
    private static final String REMINDER_USER_DUE_KEY_PREFIX = "reminder:user:due:";
    private static final Duration COMPLETED_TTL = Duration.ofDays(30);

    private final ReminderTaskJpaRepository reminderTaskJpaRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public ReminderRepository(ReminderTaskJpaRepository reminderTaskJpaRepository,
                              StringRedisTemplate redisTemplate,
                              ObjectMapper objectMapper) {
        this.reminderTaskJpaRepository = reminderTaskJpaRepository;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public ReminderRecord save(ReminderRecord reminder) {
        ReminderTaskDO task = toDataObject(reminder);
        reminderTaskJpaRepository.save(task);
        syncRedis(reminder);
        return reminder;
    }

    @Override
    public Optional<ReminderRecord> findById(String reminderId) {
        if (!StringUtils.hasText(reminderId)) {
            return Optional.empty();
        }

        Optional<ReminderRecord> cached = readFromRedis(reminderId);
        if (cached.isPresent()) {
            return cached;
        }

        return reminderTaskJpaRepository.findById(reminderId)
                .map(task -> {
                    ReminderRecord record = toRecord(task);
                    syncRedis(record);
                    return record;
                });
    }

    @Override
    public List<ReminderRecord> findPendingByUser(String userId) {
        if (!StringUtils.hasText(userId)) {
            return List.of();
        }

        List<ReminderRecord> cached = readIndexedReminders(userDueKey(userId), null, Integer.MAX_VALUE, null);
        if (!cached.isEmpty()) {
            return cached;
        }

        List<ReminderRecord> reminders = reminderTaskJpaRepository
                .findByUserIdAndStatusOrderByNextFireAtAsc(userId, ReminderStatus.ACTIVE.name())
                .stream()
                .map(this::toRecord)
                .toList();
        syncRedis(reminders);
        return reminders;
    }

    @Override
    public List<ReminderRecord> findDueReminders(Instant now, int limit) {
        if (now == null || limit <= 0) {
            return List.of();
        }

        List<ReminderRecord> cached = readIndexedReminders(REMINDER_DUE_KEY, now, limit, now);
        if (!cached.isEmpty()) {
            return cached;
        }

        List<ReminderRecord> reminders = reminderTaskJpaRepository
                .findByStatusAndNextFireAtLessThanEqualOrderByNextFireAtAsc(ReminderStatus.ACTIVE.name(), now)
                .stream()
                .map(this::toRecord)
                .limit(limit)
                .toList();
        syncRedis(reminders);
        return reminders;
    }

    @Override
    public List<ReminderRecord> findAllActive() {
        return reminderTaskJpaRepository
                .findByStatusOrderByNextFireAtAsc(ReminderStatus.ACTIVE.name())
                .stream()
                .map(this::toRecord)
                .toList();
    }

    private List<ReminderRecord> readIndexedReminders(String key, Instant maxScore, int limit, Instant dueCutoff) {
        try {
            Set<String> reminderIds = maxScore == null
                    ? redisTemplate.opsForZSet().range(key, 0, Math.max(limit - 1L, -1L))
                    : redisTemplate.opsForZSet().rangeByScore(key, Double.NEGATIVE_INFINITY, maxScore.toEpochMilli(), 0, limit);
            if (reminderIds == null || reminderIds.isEmpty()) {
                return List.of();
            }

            return reminderIds.stream()
                    .map(this::readThroughReminder)
                    .flatMap(Optional::stream)
                    .filter(ReminderRecord::isActive)
                    .filter(reminder -> dueCutoff == null || !reminder.nextFireAt().isAfter(dueCutoff))
                    .sorted(Comparator.comparing(ReminderRecord::nextFireAt))
                    .toList();
        }
        catch (Exception e) {
            log.warn("Failed to read reminder index {}", key, e);
            return List.of();
        }
    }

    private Optional<ReminderRecord> readThroughReminder(String reminderId) {
        Optional<ReminderRecord> reminder = readFromRedis(reminderId);
        if (reminder.isPresent()) {
            return reminder;
        }

        return reminderTaskJpaRepository.findById(reminderId)
                .map(task -> {
                    ReminderRecord loaded = toRecord(task);
                    syncRedis(loaded);
                    return loaded;
                });
    }

    private Optional<ReminderRecord> readFromRedis(String reminderId) {
        try {
            String json = redisTemplate.opsForValue().get(reminderKey(reminderId));
            if (!StringUtils.hasText(json)) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(json, ReminderRecord.class));
        }
        catch (Exception e) {
            log.warn("Failed to load reminder {} from redis", reminderId, e);
            return Optional.empty();
        }
    }

    private void syncRedis(List<ReminderRecord> reminders) {
        for (ReminderRecord reminder : reminders) {
            syncRedis(reminder);
        }
    }

    private void syncRedis(ReminderRecord reminder) {
        try {
            String key = reminderKey(reminder.id());
            String json = objectMapper.writeValueAsString(reminder);

            removeIndexes(reminder);
            if (reminder.status() == ReminderStatus.COMPLETED || reminder.status() == ReminderStatus.CANCELED) {
                redisTemplate.opsForValue().set(key, json, COMPLETED_TTL);
                return;
            }

            redisTemplate.opsForValue().set(key, json);
            indexReminder(reminder);
        }
        catch (Exception e) {
            log.warn("Failed to sync reminder {} to redis", reminder.id(), e);
        }
    }

    private void indexReminder(ReminderRecord reminder) {
        double score = reminder.nextFireAt().toEpochMilli();
        redisTemplate.opsForZSet().add(REMINDER_DUE_KEY, reminder.id(), score);
        redisTemplate.opsForZSet().add(userDueKey(reminder.userId()), reminder.id(), score);
    }

    private void removeIndexes(ReminderRecord reminder) {
        redisTemplate.opsForZSet().remove(REMINDER_DUE_KEY, reminder.id());
        redisTemplate.opsForZSet().remove(userDueKey(reminder.userId()), reminder.id());
    }

    private ReminderTaskDO toDataObject(ReminderRecord reminder) {
        ReminderTaskDO task = new ReminderTaskDO();
        task.setId(reminder.id());
        task.setUserId(reminder.userId());
        task.setContent(reminder.content());
        task.setOriginalRequest(reminder.originalRequest());
        task.setScheduleLabel(reminder.scheduleLabel());
        task.setRecurrence(reminder.recurrence().name());
        task.setStatus(reminder.status().name());
        task.setAction(reminder.action() != null && reminder.action() != ReminderAction.NONE
                ? reminder.action().name() : null);
        task.setTimezone(reminder.timezone());
        task.setNextFireAt(reminder.nextFireAt());
        task.setCronExpression(reminder.cronExpression());
        task.setIntervalValue(reminder.intervalValue());
        task.setIntervalUnit(reminder.intervalUnit() != null ? reminder.intervalUnit().name() : null);
        task.setCreatedAt(reminder.createdAt());
        task.setUpdatedAt(reminder.updatedAt());
        task.setCompletedAt(reminder.completedAt());
        task.setLastTriggeredAt(reminder.lastTriggeredAt());
        return task;
    }

    private ReminderRecord toRecord(ReminderTaskDO task) {
        return new ReminderRecord(
                task.getId(),
                task.getUserId(),
                task.getContent(),
                task.getOriginalRequest(),
                task.getScheduleLabel(),
                ReminderRecurrence.valueOf(task.getRecurrence()),
                task.getAction() != null ? ReminderAction.valueOf(task.getAction()) : ReminderAction.NONE,
                ReminderStatus.valueOf(task.getStatus()),
                task.getTimezone(),
                task.getNextFireAt(),
                task.getCronExpression(),
                task.getIntervalValue(),
                StringUtils.hasText(task.getIntervalUnit()) ? ScheduleIntervalUnit.valueOf(task.getIntervalUnit()) : null,
                task.getCreatedAt(),
                task.getUpdatedAt(),
                task.getCompletedAt(),
                task.getLastTriggeredAt()
        );
    }

    private String reminderKey(String reminderId) {
        return REMINDER_KEY_PREFIX + reminderId;
    }

    private String userDueKey(String userId) {
        return REMINDER_USER_DUE_KEY_PREFIX + userId;
    }
}
