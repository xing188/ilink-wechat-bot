CREATE TABLE reminder_task (
                               id                CHAR(32)      NOT NULL COMMENT '提醒ID，使用去掉横杠的UUID',
                               user_id           VARCHAR(128)  NOT NULL COMMENT '用户ID/微信ID',
                               content           VARCHAR(500)  NOT NULL COMMENT '提醒内容',
                               original_request  VARCHAR(500)  NOT NULL COMMENT '用户原始输入',
                               schedule_label    VARCHAR(100)  NOT NULL COMMENT '展示用时间描述',
                               recurrence        VARCHAR(16)   NOT NULL COMMENT '重复类型：ONCE / DAILY',
                               status            VARCHAR(16)   NOT NULL COMMENT '状态：ACTIVE / COMPLETED / CANCELED',
                               timezone          VARCHAR(64)   NOT NULL COMMENT '时区，例如 Asia/Shanghai',
                               next_fire_at      DATETIME(3)   NOT NULL COMMENT '下一次触发时间',
                               created_at        DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
                               updated_at        DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
                               completed_at      DATETIME(3)   NULL COMMENT '完成时间',
                               last_triggered_at DATETIME(3)   NULL COMMENT '上一次实际触发时间',

                               PRIMARY KEY (id),
                               KEY idx_reminder_user_status_fire (user_id, status, next_fire_at),
                               KEY idx_reminder_status_fire (status, next_fire_at),
                               KEY idx_reminder_completed_at (completed_at)
) ENGINE=InnoDB
    DEFAULT CHARSET=utf8mb4
    COMMENT='提醒任务表';