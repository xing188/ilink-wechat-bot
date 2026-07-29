package com.ilink.test.ilinkcheck.application.agent.orchestrator.runtime;

import com.ilink.test.ilinkcheck.application.agent.orchestrator.model.AgentState;

import java.util.Optional;

/**
 * Agent 状态存储抽象。
 * 当前先给内存实现，后续可以换成 Redis / 数据库而不影响主循环。
 */
public interface AgentStateStore {

    Optional<AgentState> findActive(String conversationId);

    void save(AgentState state);

    void remove(String conversationId);
}
