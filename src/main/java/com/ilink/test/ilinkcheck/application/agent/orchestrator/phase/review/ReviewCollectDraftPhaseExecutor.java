package com.ilink.test.ilinkcheck.application.agent.orchestrator.phase.review;

import com.ilink.test.ilinkcheck.application.agent.orchestrator.model.*;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.support.AgentTextUtils;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 审稿流程第一阶段。
 * 负责等待用户把待审稿件贴过来。
 */
@Service
public class ReviewCollectDraftPhaseExecutor implements PhaseExecutor {

    @Override
    public boolean supports(AgentTaskType taskType, AgentPhase phase) {
        return taskType == AgentTaskType.DOC_REVIEW && phase == AgentPhase.REVIEW_RETRIEVE_GUIDE;
    }

    @Override
    public PhaseExecutionResult execute(AgentState state) {
        // 优先取确认阶段已保存的草稿
        String draftText = state.getArtifact(AgentArtifacts.REQUEST_DRAFT, String.class);
        if (draftText == null) {
            draftText = AgentTextUtils.extractDraftText(state.getLatestUserMessage())
                    .or(() -> AgentTextUtils.extractDraftText(state.getOriginalUserMessage()))
                    .orElse(null);
        }

        if (draftText == null) {
            return PhaseExecutionResult.waitForUser(
                    AgentPhase.REVIEW_RETRIEVE_GUIDE,
                    "请把要审的技术文档或博客草稿直接贴给我，我会按规范帮你审稿。",
                    Map.of()
            );
        }

        return PhaseExecutionResult.next(AgentPhase.REVIEW_GENERATE, Map.of(
                AgentArtifacts.REQUEST_DRAFT, draftText
        ));
    }
}
