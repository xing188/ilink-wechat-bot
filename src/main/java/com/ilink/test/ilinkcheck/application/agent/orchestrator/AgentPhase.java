package com.ilink.test.ilinkcheck.application.agent.orchestrator;

/**
 * Agent 工作流阶段。
 * 大循环按阶段推进；每个阶段只做一件明确的事。
 */
public enum AgentPhase {
    HOTSPOT_FETCH,
    SENTIMENT_ANALYZE,
    TOPIC_GENERATE,
    WAIT_USER_SELECTION,
    DRAFT_GENERATE,
    FINISH,
    FAILED
}
