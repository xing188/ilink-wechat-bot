package com.ilink.test.ilinkcheck.application.agent.orchestrator.phase.digest;

import com.ilink.test.ilinkcheck.application.agent.orchestrator.model.*;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.support.AgentTextUtils;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 晨报流程第一阶段。
 * 负责从用户输入中拿到订阅时间。
 */
@Service
public class DigestCollectSchedulePhaseExecutor implements PhaseExecutor {

    @Override
    public boolean supports(AgentTaskType taskType, AgentPhase phase) {
        return taskType == AgentTaskType.DAILY_DIGEST_SUBSCRIBE && phase == AgentPhase.DIGEST_FETCH;
    }

    @Override
    public PhaseExecutionResult execute(AgentState state) {
        String scheduleTime = AgentTextUtils.extractScheduleTime(state.getLatestUserMessage())
                .or(() -> AgentTextUtils.extractScheduleTime(state.getOriginalUserMessage()))
                .orElse(null);

        if (scheduleTime == null) {
            return PhaseExecutionResult.waitForUser(
                    AgentPhase.DIGEST_FETCH,
                    "你希望我每天几点推送技术晨报？请直接回复 `08:30` 这样的时间。",
                    Map.of()
            );
        }

        return PhaseExecutionResult.next(AgentPhase.DIGEST_COMPOSE, Map.of(
                AgentArtifacts.REQUEST_SCHEDULE_TIME, scheduleTime
        ));
    }
}
