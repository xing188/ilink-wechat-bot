package com.ilink.test.ilinkcheck.application.agent.orchestrator.runtime;

import com.ilink.test.ilinkcheck.application.agent.orchestrator.model.AgentState;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 第一版最小可运行实现：单机内存态工作流状态存储。
 */
@Service
public class InMemoryAgentStateStore implements AgentStateStore {

    private final ConcurrentMap<String, AgentState> stateMap = new ConcurrentHashMap<>();

    @Override
    public Optional<AgentState> findActive(String conversationId) {
        AgentState state = stateMap.get(conversationId);
        if (state == null || state.isFinished()) {
            return Optional.empty();
        }
        return Optional.of(state);
    }

    @Override
    public void save(AgentState state) {
        if (state == null) {
            return;
        }
        if (state.isFinished()) {
            stateMap.remove(state.getConversationId());
            return;
        }
        stateMap.put(state.getConversationId(), state);
    }

    @Override
    public void remove(String conversationId) {
        stateMap.remove(conversationId);
    }
}
