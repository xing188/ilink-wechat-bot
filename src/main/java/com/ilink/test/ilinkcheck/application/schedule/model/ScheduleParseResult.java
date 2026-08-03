package com.ilink.test.ilinkcheck.application.schedule.model;

/**
 * 统一的定时解析结果。
 */
public record ScheduleParseResult(
        ScheduleParseStatus status,
        SchedulePlan plan,
        String matchedText,
        String prompt
) {

    public boolean resolved() {
        return status == ScheduleParseStatus.RESOLVED && plan != null;
    }

    public boolean ambiguous() {
        return status == ScheduleParseStatus.AMBIGUOUS;
    }
}
