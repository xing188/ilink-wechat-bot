package com.ilink.test.ilinkcheck.infrastructure.persistence.reminder;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import org.apache.ibatis.type.InstantTypeHandler;

import java.time.Instant;

@Data
@TableName(value = "reminder_task", autoResultMap = true)
public class ReminderTaskDO {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    @TableField("user_id")
    private String userId;

    @TableField("content")
    private String content;

    @TableField("original_request")
    private String originalRequest;

    @TableField("schedule_label")
    private String scheduleLabel;

    @TableField("recurrence")
    private String recurrence;

    @TableField("status")
    private String status;

    @TableField("timezone")
    private String timezone;

    @TableField(value = "next_fire_at", typeHandler = InstantTypeHandler.class)
    private Instant nextFireAt;

    @TableField(value = "created_at", typeHandler = InstantTypeHandler.class)
    private Instant createdAt;

    @TableField(value = "updated_at", typeHandler = InstantTypeHandler.class)
    private Instant updatedAt;

    @TableField(value = "completed_at", typeHandler = InstantTypeHandler.class)
    private Instant completedAt;

    @TableField(value = "last_triggered_at", typeHandler = InstantTypeHandler.class)
    private Instant lastTriggeredAt;
}
