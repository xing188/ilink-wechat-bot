package com.ilink.test.ilinkcheck.application.agent.orchestrator.phase.review;

import com.ilink.test.ilinkcheck.application.agent.feature.review.DraftReviewService;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.model.AgentArtifacts;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.model.AgentPhase;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.model.AgentState;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.model.AgentTaskType;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.model.PhaseExecutionResult;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.model.PhaseExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 文档审稿任务：【生成修改稿阶段】
 * 前置阶段：REVIEW_GENERATE（审稿报告已输出，并询问用户是否需要修改稿）
 *
 * <p>职责：根据用户在审稿报告后的回复，
 * 确认时调用 {@link DraftReviewService#applyFixes} 自动修复草稿，
 * 并把修改好的稿件直接返回给用户；拒绝时结束流程。</p>
 *
 * 归属任务：AgentTaskType.DOC_REVIEW
 * 绑定阶段：AgentPhase.REVIEW_FIX_GENERATE
 */
@Slf4j
@Service
public class ReviewFixGeneratePhaseExecutor implements PhaseExecutor {

    private static final List<String> CONFIRM_WORDS = List.of(
            "好", "可以", "要", "发送", "优化", "一键优化", "确认", "yes", "发我"
    );
    private static final List<String> REJECT_WORDS = List.of(
            "不用", "不需要", "算了", "不要", "no", "不了", "跳过", "不审"
    );

    private static final String FIXED_DRAFT_PREFIX = "✅ 已按规范帮你优化稿件，可直接使用：";

    private final DraftReviewService draftReviewService;

    public ReviewFixGeneratePhaseExecutor(DraftReviewService draftReviewService) {
        this.draftReviewService = draftReviewService;
    }

    @Override
    public boolean supports(AgentTaskType taskType, AgentPhase phase) {
        return taskType == AgentTaskType.DOC_REVIEW && phase == AgentPhase.REVIEW_FIX_GENERATE;
    }

    @Override
    public PhaseExecutionResult execute(AgentState state) {
        String latest = state.getLatestUserMessage();

        // 用户拒绝：跳过修改稿发送
        if (containsAny(latest, REJECT_WORDS)) {
            log.info("conversationId={}, phase=REVIEW_FIX_GENERATE, result=rejected", state.getConversationId());
            return PhaseExecutionResult.finish("好的，已跳过修改稿的发送。有需要时随时回复「一键优化」。", Map.of());
        }

        // 非确认也非拒绝：继续等待明确回复
        if (!containsAny(latest, CONFIRM_WORDS)) {
            return PhaseExecutionResult.waitForUser(
                    AgentPhase.REVIEW_FIX_GENERATE,
                    "需要我帮你发送修改好的稿件吗？回复「好」直接发送，回复「不用」跳过。",
                    Map.of()
            );
        }

        // 用户确认：从工件读取草稿并自动修复
        String draftText = state.getArtifact(AgentArtifacts.REQUEST_DRAFT, String.class);
        if (draftText == null || draftText.isBlank()) {
            log.warn("conversationId={}, phase=REVIEW_FIX_GENERATE, draft artifact missing", state.getConversationId());
            return PhaseExecutionResult.finish("未找到待优化的稿件，请重新发送文档草稿。", Map.of());
        }

        String fixedText = draftReviewService.applyFixes(draftText);
        log.info("conversationId={}, phase=REVIEW_FIX_GENERATE, fix complete, length={}",
                state.getConversationId(), fixedText.length());

        return PhaseExecutionResult.finish(FIXED_DRAFT_PREFIX + "\n\n" + fixedText, Map.of(
                AgentArtifacts.REVIEW_FIXED_TEXT, fixedText
        ));
    }

    private boolean containsAny(String text, List<String> candidates) {
        if (text == null) {
            return false;
        }
        return candidates.stream().anyMatch(text::contains);
    }
}
