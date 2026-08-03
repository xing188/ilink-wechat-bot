package com.ilink.test.ilinkcheck.application.schedule.model;

/**
 * 定时解析结果状态。
 * RESOLVED 表示已经拿到了完整可执行的规则；
 * AMBIGUOUS 表示能看懂一部分，但还需要继续追问；
 * ABSENT 表示完全没识别出可用的定时表达。
 */
public enum ScheduleParseStatus {
    RESOLVED,
    AMBIGUOUS,
    ABSENT
}
