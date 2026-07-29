package com.ilink.test.ilinkcheck.application.agent.orchestrator;

import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.Map;

/**
 * 工作流模板注册表。
 * 第一版先只定义每种任务的起始阶段，后续可扩展为完整模板。
 */
@Service
public class WorkflowTemplateRegistry {

    private final Map<AgentTaskType, AgentPhase> startPhases = new EnumMap<>(AgentTaskType.class);

    public WorkflowTemplateRegistry() {
        startPhases.put(AgentTaskType.HOTSPOT_ANALYSIS, AgentPhase.HOTSPOT_FETCH);
    }

    public AgentPhase startPhase(AgentTaskType taskType) {
        AgentPhase phase = startPhases.get(taskType);
        if (phase == null) {
            throw new IllegalArgumentException("Unsupported taskType: " + taskType);
        }
        return phase;
    }
}
