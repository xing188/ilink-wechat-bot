package com.ilink.test.ilinkcheck.infrastructure.persistence.reminder;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.Instant;

@Data
@Entity
@Table(name = "reminder_task")
public class ReminderTaskDO {

    @Id
    @Column(name = "id", length = 32, nullable = false)
    private String id;

    @Column(name = "user_id", length = 128, nullable = false)
    private String userId;

    @Column(name = "content", length = 500, nullable = false)
    private String content;

    @Column(name = "original_request", length = 500, nullable = false)
    private String originalRequest;

    @Column(name = "schedule_label", length = 100, nullable = false)
    private String scheduleLabel;

    @Column(name = "recurrence", length = 16, nullable = false)
    private String recurrence;

    @Column(name = "status", length = 16, nullable = false)
    private String status;

    @Column(name = "timezone", length = 64, nullable = false)
    private String timezone;

    @Column(name = "next_fire_at", nullable = false)
    private Instant nextFireAt;

    @Column(name = "cron_expression", length = 100)
    private String cronExpression;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "last_triggered_at")
    private Instant lastTriggeredAt;
}
