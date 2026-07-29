package com.ilink.test.ilinkcheck.application.agent.orchestrator.runtime;

import com.ilink.test.ilinkcheck.application.agent.orchestrator.model.AgentPhase;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.model.AgentTaskType;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.Map;

/**
 * 工作流模板注册表。
 * 当前只维护每个任务的起始阶段；如果后续需要完整模板，再在这里扩展。
 */
@Service
public class WorkflowTemplateRegistry {

    private final Map<AgentTaskType, AgentPhase> startPhases = new EnumMap<>(AgentTaskType.class);

    public WorkflowTemplateRegistry() {
        startPhases.put(AgentTaskType.GITHUB_REPO_DIGEST, AgentPhase.GITHUB_FETCH);
        startPhases.put(AgentTaskType.TECH_ROADMAP, AgentPhase.ROADMAP_FETCH);
        startPhases.put(AgentTaskType.DOC_REVIEW, AgentPhase.REVIEW_COLLECT_DRAFT);
        startPhases.put(AgentTaskType.DAILY_DIGEST_SUBSCRIBE, AgentPhase.DIGEST_COLLECT_SCHEDULE);
    }

    public AgentPhase startPhase(AgentTaskType taskType) {
        AgentPhase phase = startPhases.get(taskType);
        if (phase == null) {
            throw new IllegalArgumentException("Unsupported taskType: " + taskType);
        }
        return phase;
    }
}
