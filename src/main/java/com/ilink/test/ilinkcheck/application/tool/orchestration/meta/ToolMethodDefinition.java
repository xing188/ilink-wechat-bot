package com.ilink.test.ilinkcheck.application.tool.orchestration.meta;

import org.springframework.ai.tool.ToolCallback;

/**
 * 一个具体 @Tool 方法在运行时的定义。
 * 它已经被转换成 Spring AI 可调用的 ToolCallback。
 *
 * @param toolId 业务层工具 id，例如 weather、reminder
 * @param callbackName Spring AI 实际调用的方法名，例如 queryWeather
 * @param callback 真正执行该工具方法的回调对象
 * @param readOnly 该方法是否被视为只读方法
 * @param description 该工具方法的描述文本
 * @param inputSchema 该工具方法的入参 JSON Schema
 */
public record ToolMethodDefinition(
        String toolId,
        String callbackName,
        ToolCallback callback,
        boolean readOnly,
        String description,
        String inputSchema
) {
}
