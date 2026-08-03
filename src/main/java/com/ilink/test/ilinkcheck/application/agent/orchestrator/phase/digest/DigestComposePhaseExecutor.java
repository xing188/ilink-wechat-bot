package com.ilink.test.ilinkcheck.application.agent.orchestrator.phase.digest;

import com.ilink.test.ilinkcheck.application.agent.dto.digest.DigestContent;
import com.ilink.test.ilinkcheck.application.agent.feature.digest.DailyDigestComposeService;
import com.ilink.test.ilinkcheck.application.agent.feature.digest.DailyDigestSubscriptionService;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.model.AgentArtifacts;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.model.AgentPhase;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.model.AgentState;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.model.AgentTaskType;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.model.PhaseExecutionResult;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.model.PhaseExecutor;
import com.ilink.test.ilinkcheck.application.reminder.model.ReminderRecurrence;
import com.ilink.test.ilinkcheck.application.schedule.model.ScheduleParseResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 晨报工作流的内容组装阶段。
 * 负责在拿到推送计划后生成预览，同时把订阅真正登记到订阅服务和调度器。
 */
@Service
public class DigestComposePhaseExecutor implements PhaseExecutor {

    private final DailyDigestComposeService dailyDigestComposeService;
    private final DailyDigestSubscriptionService dailyDigestSubscriptionService;

    @Autowired
    public DigestComposePhaseExecutor(DailyDigestComposeService dailyDigestComposeService,
                                      DailyDigestSubscriptionService dailyDigestSubscriptionService) {
        this.dailyDigestComposeService = dailyDigestComposeService;
        this.dailyDigestSubscriptionService = dailyDigestSubscriptionService;
    }

    @Override
    public boolean supports(AgentTaskType taskType, AgentPhase phase) {
        return taskType == AgentTaskType.DAILY_DIGEST_SUBSCRIBE && phase == AgentPhase.DIGEST_COMPOSE;
    }

    @Override
    public PhaseExecutionResult execute(AgentState state) {
        String scheduleText = state.getArtifact(AgentArtifacts.REQUEST_SCHEDULE_TIME, String.class);
        ScheduleParseResult parsed = dailyDigestSubscriptionService.resolveSchedulePlan(scheduleText);
        if (!parsed.resolved() || parsed.plan().recurrence() == ReminderRecurrence.ONCE) {
            return PhaseExecutionResult.finish(
                    parsed.prompt() != null ? parsed.prompt() : "晨报订阅需要重复执行，请提供每天/每周/每月类计划。",
                    Map.of()
            );
        }

        DigestContent digestContent = dailyDigestComposeService.collect();

        // 当前最小实现直接把 conversationId 作为订阅接收方标识。
        // 在微信入口里，这个值就是 fromUserId，因此可以直接用于后续定时推送。
        dailyDigestSubscriptionService.subscribe(state.getConversationId(), parsed.plan());

        String reply = dailyDigestComposeService.buildPreview(parsed.plan().scheduleLabel(), digestContent);
        return PhaseExecutionResult.finish(reply, Map.of(
                AgentArtifacts.DIGEST_CONTENT, digestContent
        ));
    }
}
