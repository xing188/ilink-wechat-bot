package com.ilink.test.ilinkcheck.infrastructure.monitor;

import com.ilink.test.ilinkcheck.application.agent.orchestrator.model.AgentPhase;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.model.AgentTaskType;

import java.time.Instant;

/**
 * Agent 工作流运行时事件（供监控台实时展示）。
 * 覆盖：路由命中 → 阶段推进 → 等待输入 → 完成/取消/失败 的完整状态机流转。
 */
public record AgentEvent(
        Instant timestamp,
        String conversationId,
        AgentTaskType taskType,
        AgentPhase phase,
        String event,
        String userMessage,
        String reply,
        String topic,
        String repository,
        String draft,
        String scheduleText
) {
}
