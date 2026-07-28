package com.ilink.test.ilinkcheck.application.tool.orchestration.model;

import java.util.Map;

/**
 * 归一化后的可执行步骤。
 * 这里的 toolName 指向的是具体的 Spring AI callback 名称，
 * 不是业务层面的 toolId。
 *
 * @param toolName 真实的 callback 名称，例如 queryWeather
 * @param arguments 调用该 callback 时的归一化参数
 * @param purpose 这一步的业务目的说明，主要给日志和总结使用
 */
public record ToolExecutionStep(
        String toolName,
        Map<String, Object> arguments,
        String purpose
) {
}
