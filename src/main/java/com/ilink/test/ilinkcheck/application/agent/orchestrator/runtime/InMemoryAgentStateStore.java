package com.ilink.test.ilinkcheck.application.agent.orchestrator.runtime;

import com.ilink.test.ilinkcheck.application.agent.orchestrator.model.AgentState;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.model.AgentTaskType;
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
        if (state == null) {
            return Optional.empty();
        }
        // 已完成的技术路线工作流需保留，以支持后续"翻译成中文"重渲染指令；
        // 已完成的技术路线 / GitHub 仓库速读工作流需保留，以支持后续目录展开等指令；
        // 其它工作流完成后即视为非活跃。
        if (state.isFinished() && !isRetainedAfterFinish(state)) {
            return Optional.empty();
        }
        return Optional.of(state);
    }

    @Override
    public void save(AgentState state) {
        if (state == null) {
            return;
        }
        if (state.isFinished() && !isRetainedAfterFinish(state)) {
            stateMap.remove(state.getConversationId());
            return;
        }
        stateMap.put(state.getConversationId(), state);
    }

    @Override
    public void remove(String conversationId) {
        stateMap.remove(conversationId);
    }

    /**
     * 完成态是否需要在存储中保留（用于支持后续指令）。
     */
    private boolean isRetainedAfterFinish(AgentState state) {
        return state.getTaskType() == AgentTaskType.TECH_ROADMAP
                || state.getTaskType() == AgentTaskType.GITHUB_REPO_DIGEST;
    }
}
