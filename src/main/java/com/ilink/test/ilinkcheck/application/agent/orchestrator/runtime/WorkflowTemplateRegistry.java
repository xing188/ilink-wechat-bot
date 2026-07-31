package com.ilink.test.ilinkcheck.application.agent.orchestrator.runtime;

import com.ilink.test.ilinkcheck.application.agent.orchestrator.model.AgentPhase;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.model.AgentTaskType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.Map;

/**
 * Agent 工作流模板注册表
 * 职责：维护每种任务类型对应的工作流基础信息
 * 当前极简实现：仅保存每个任务启动后的【第一个执行阶段】
 *
 * 扩展规划（后续迭代）：
 * 如果需要定义完整阶段流转链（phaseA -> phaseB -> phaseC），
 * 可在此处定义 WorkflowTemplate 对象，包含全阶段有序列表，统一管理流转模板。
 */
@Slf4j
@Service
public class WorkflowTemplateRegistry {

    /**
     * TaskType -> 工作流起始阶段
     * 使用EnumMap：枚举作为key，查询效率高、内存更优
     */
    private final Map<AgentTaskType, AgentPhase> startPhases = new EnumMap<>(AgentTaskType.class);

    public WorkflowTemplateRegistry() {
        registerAllTaskStartPhase();
    }

    /**
     * 注册所有任务对应的起始阶段
     */
    private void registerAllTaskStartPhase() {
        startPhases.put(AgentTaskType.GITHUB_REPO_DIGEST, AgentPhase.GITHUB_FETCH);
        startPhases.put(AgentTaskType.TECH_ROADMAP, AgentPhase.ROADMAP_FETCH);
        startPhases.put(AgentTaskType.DOC_REVIEW, AgentPhase.REVIEW_RETRIEVE_GUIDE);
        startPhases.put(AgentTaskType.DAILY_DIGEST_SUBSCRIBE, AgentPhase.DIGEST_FETCH);
        log.info("WorkflowTemplateRegistry init, registered task count:{}", startPhases.size());
    }

    /**
     * 获取指定任务类型的启动阶段
     * @param taskType agent任务类型
     * @return 工作流第一个执行阶段
     */
    public AgentPhase startPhase(AgentTaskType taskType) {
        AgentPhase phase = startPhases.get(taskType);
        if (phase == null) {
            String errMsg = "找不到任务对应的起始阶段，taskType=" + taskType;
            log.error(errMsg);
            throw new IllegalArgumentException(errMsg);
        }
        return phase;
    }
}
