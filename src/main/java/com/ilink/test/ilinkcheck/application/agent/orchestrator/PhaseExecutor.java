package com.ilink.test.ilinkcheck.application.agent.orchestrator;

/**
 * 阶段执行器。
 * 每个执行器只处理“某个任务类型 + 某个阶段”的业务逻辑。
 */
public interface PhaseExecutor {

    boolean supports(AgentTaskType taskType, AgentPhase phase);

    PhaseExecutionResult execute(AgentState state);
}
