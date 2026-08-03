package com.ilink.test.ilinkcheck.application.agent.orchestrator.model;

/**
 * Agent 工作流阶段。
 * 每个阶段只做一件明确的事，主循环只负责推进，不写具体业务。
 */
public enum AgentPhase {
    GITHUB_FETCH,
    GITHUB_SUMMARIZE,
    GITHUB_DIRECTORY_ANALYZE,
    GITHUB_DIRECTORY_EXPAND,
    GITHUB_CODE_EXPLAIN,
    ROADMAP_FETCH,
    ROADMAP_COMPOSE,
    REVIEW_CONFIRM,
    REVIEW_RETRIEVE_GUIDE,
    REVIEW_GENERATE,
    REVIEW_FIX_GENERATE,
    DIGEST_FETCH,
    DIGEST_COMPOSE,
    WAIT_USER_INPUT,
    FINISH,
    FAILED
}
