package com.ilink.test.ilinkcheck.application.agent.orchestrator.model;

/**
 * Agent 工作流阶段。
 * 每个阶段只做一件明确的事，主循环只负责推进，不写具体业务。
 */
public enum AgentPhase {
    GITHUB_FETCH,
    GITHUB_SUMMARIZE,
    ROADMAP_FETCH,
    ROADMAP_COMPOSE,
    REVIEW_COLLECT_DRAFT,
    REVIEW_GENERATE,
    DIGEST_COLLECT_SCHEDULE,
    DIGEST_COMPOSE,
    FINISH,
    FAILED
}
