package com.ilink.test.ilinkcheck.application.agent.feature.review;

import com.ilink.test.ilinkcheck.application.agent.dto.review.ReviewReport;
import com.ilink.test.ilinkcheck.application.agent.dto.review.ReviewSuggestion;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.model.*;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 文档审稿阶段执行器（人5 实现）。
 * <p>
 * 负责在 {@link AgentPhase#REVIEW_GENERATE} 阶段调用 {@link DraftReviewService}
 * 对用户提交的技术文档草稿进行规范化审稿，并输出结构化的审稿报告。
 * 报告输出后不立即结束，而是询问用户是否需要发送修改好的稿件，
 * 由 {@link com.ilink.test.ilinkcheck.application.agent.orchestrator.phase.review.ReviewFixGeneratePhaseExecutor}
 * 在 {@link AgentPhase#REVIEW_FIX_GENERATE} 阶段处理用户的确认结果。
 * </p>
 *
 * <p>该执行器是审稿主流程的核心阶段，前置阶段
 * {@link AgentPhase#REVIEW_RETRIEVE_GUIDE} 负责收集用户输入的草稿文本。</p>
 *
 * <p>边界：审稿逻辑在 feature.review，向量检索在 infrastructure.rag。
 * 不直接写 HTTP 请求，不改公共入口。</p>
 */
@Service
@Order(0)
public class DocReviewPhaseExecutor implements PhaseExecutor {

    private final DraftReviewService draftReviewService;

    public DocReviewPhaseExecutor(DraftReviewService draftReviewService) {
        this.draftReviewService = draftReviewService;
    }

    @Override
    public boolean supports(AgentTaskType taskType, AgentPhase phase) {
        return taskType == AgentTaskType.DOC_REVIEW && phase == AgentPhase.REVIEW_GENERATE;
    }

    @Override
    public PhaseExecutionResult execute(AgentState state) {
        // 从工作流上下文中获取用户提交的草稿文本
        String draftText = state.getArtifact(AgentArtifacts.REQUEST_DRAFT, String.class);

        if (draftText == null || draftText.isBlank()) {
            return PhaseExecutionResult.finish(
                    "未找到待审稿的文本内容，请重新发送文档草稿。",
                    Map.of()
            );
        }

        // 调用审稿服务执行完整的审稿流程（规则引擎 + RAG 检索）
        ReviewReport report = draftReviewService.review(draftText);
        String formattedReport = draftReviewService.formatReport(report);

        // 报告输出后暂停，询问用户是否需要发送修改好的稿件；
        // 用户回复「好」后由 REVIEW_FIX_GENERATE 阶段生成修改稿，回复「不用」则跳过。
        String reply = formattedReport
                + "\n\n👉 需要我帮你发送修改好的稿件吗？回复「好」直接发送，回复「不用」跳过。";

        return PhaseExecutionResult.waitForUser(
                AgentPhase.REVIEW_FIX_GENERATE,
                reply,
                Map.of(
                        AgentArtifacts.REQUEST_DRAFT, draftText,
                        AgentArtifacts.REVIEW_SUGGESTIONS, report.suggestions(),
                        AgentArtifacts.REVIEW_REPORT, report
                )
        );
    }
}
