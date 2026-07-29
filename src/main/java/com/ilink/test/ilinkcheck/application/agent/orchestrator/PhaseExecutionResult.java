package com.ilink.test.ilinkcheck.application.agent.orchestrator;

import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * 单个阶段的执行结果。
 * 执行器只负责说明“下一步去哪、要不要等用户、要返回什么”，主循环负责真正推进状态。
 */
public record PhaseExecutionResult(
        AgentPhase nextPhase,
        Map<String, Object> artifactsDelta,
        String replyToUser,
        boolean needUserInput,
        boolean finished
) {

    public PhaseExecutionResult {
        artifactsDelta = artifactsDelta == null ? Map.of() : Map.copyOf(artifactsDelta);
        replyToUser = StringUtils.hasText(replyToUser) ? replyToUser.trim() : null;
    }

    public static PhaseExecutionResult next(AgentPhase nextPhase, Map<String, Object> artifactsDelta) {
        return new PhaseExecutionResult(nextPhase, artifactsDelta, null, false, false);
    }

    public static PhaseExecutionResult waitForUser(AgentPhase nextPhase,
                                                   String replyToUser,
                                                   Map<String, Object> artifactsDelta) {
        return new PhaseExecutionResult(nextPhase, artifactsDelta, replyToUser, true, false);
    }

    public static PhaseExecutionResult finish(String replyToUser, Map<String, Object> artifactsDelta) {
        return new PhaseExecutionResult(AgentPhase.FINISH, artifactsDelta, replyToUser, false, true);
    }
}
