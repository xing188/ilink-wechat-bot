package com.ilink.test.ilinkcheck.application.agent.orchestrator.phase.review;

import com.ilink.test.ilinkcheck.application.agent.orchestrator.model.AgentArtifacts;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.model.AgentPhase;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.model.AgentState;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.model.AgentTaskType;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.model.PhaseExecutionResult;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.model.PhaseExecutor;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.support.AgentTextUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 文档审稿任务：第零阶段【审稿确认阶段】。
 * 用户直接贴长文本（文章/草稿/代码）时，先做简短回复并询问是否需要审稿，
 * 避免把所有长消息都强制当作审稿请求。
 */
@Slf4j
@Service
public class ReviewConfirmPhaseExecutor implements PhaseExecutor {

    private static final List<String> REVIEW_INTENT_HINTS = List.of("审稿", "审一下", "审阅", "帮我审", "review", "排版", "文档规范");
    private static final List<String> CONFIRM_WORDS = List.of("好", "可以", "审", "是", "确认", "yes", "要", "审吧", "开始");
    private static final List<String> REJECT_WORDS = List.of("不用", "不需要", "算了", "不要", "no", "不了", "跳过", "不审");
    private static final int PREVIEW_LENGTH = 60;

    @Override
    public boolean supports(AgentTaskType taskType, AgentPhase phase) {
        return taskType == AgentTaskType.DOC_REVIEW && phase == AgentPhase.REVIEW_CONFIRM;
    }

    @Override
    public PhaseExecutionResult execute(AgentState state) {
        String latest = state.getLatestUserMessage();
        // 用 artifact 判断是否已保存草稿：已保存说明上一条是长文本，本条是用户回应确认
        boolean hasPendingDraft = state.hasArtifact(AgentArtifacts.REQUEST_DRAFT);

        // 用户已经明确表达审稿意图（如"帮我审一下这篇..."），直接进入审稿
        if (containsAny(latest, REVIEW_INTENT_HINTS)) {
            return proceedToReview(state);
        }

        // 已有待确认草稿 → 本条消息是用户对"是否审稿"的回应
        if (hasPendingDraft) {
            if (containsAny(latest, REJECT_WORDS)) {
                log.info("conversationId={}, review workflow rejected by user", state.getConversationId());
                return PhaseExecutionResult.finish("好的，暂不审稿。有需要随时告诉我。", Map.of());
            }
            if (containsAny(latest, CONFIRM_WORDS)) {
                return proceedToReview(state);
            }
            return PhaseExecutionResult.waitForUser(
                    AgentPhase.REVIEW_CONFIRM,
                    "没太明白你的意思。需要我帮你审稿吗？回复「好」开始，回复「不用」跳过。",
                    Map.of()
            );
        }

        // 首次进入：提取文章草稿，保存后询问是否需要审稿
        Optional<String> draftOpt = AgentTextUtils.extractDraftText(latest);
        if (draftOpt.isPresent()) {
            String draft = draftOpt.get();
            String preview = draft.length() > PREVIEW_LENGTH
                    ? draft.substring(0, PREVIEW_LENGTH) + "…"
                    : draft;
            String reply = "收到一段内容：\n\"" + preview + "\"\n\n"
                    + "需要我按技术文档规范帮你审稿吗？回复「好」开始审稿，回复「不用」跳过。";
            return PhaseExecutionResult.waitForUser(
                    AgentPhase.REVIEW_CONFIRM,
                    reply,
                    Map.of(AgentArtifacts.REQUEST_DRAFT, draft)
            );
        }

        // 极短消息且无审稿意图：交给后续阶段，让 CollectDraft 提示用户贴文章
        return proceedToReview(state);
    }

    private PhaseExecutionResult proceedToReview(AgentState state) {
        String draft = state.getArtifact(AgentArtifacts.REQUEST_DRAFT, String.class);
        if (draft != null) {
            return PhaseExecutionResult.next(AgentPhase.REVIEW_RETRIEVE_GUIDE, Map.of());
        }
        return PhaseExecutionResult.next(AgentPhase.REVIEW_RETRIEVE_GUIDE, Map.of());
    }

    private boolean containsAny(String text, List<String> candidates) {
        if (text == null) {
            return false;
        }
        return candidates.stream().anyMatch(text::contains);
    }
}
