package com.ilink.test.ilinkcheck.application.agent.orchestrator.runtime;

import com.ilink.test.ilinkcheck.application.agent.orchestrator.model.AgentPhase;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.model.AgentTaskType;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.model.PhaseExecutor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 阶段执行器注册表。
 * 主循环通过它按 taskType + phase 查找真正的执行器实现。
 */
@Service
public class PhaseExecutorRegistry {

    private final List<PhaseExecutor> executors;

    public PhaseExecutorRegistry(List<PhaseExecutor> executors) {
        this.executors = List.copyOf(executors);
    }

    public PhaseExecutor findRequired(AgentTaskType taskType, AgentPhase phase) {
        return executors.stream()
                .filter(executor -> executor.supports(taskType, phase))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No phase executor found for taskType=%s, phase=%s".formatted(taskType, phase)));
    }
}
