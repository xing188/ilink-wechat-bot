package com.ilink.test.ilinkcheck.application.agent.orchestrator.phase.review;

import com.ilink.test.ilinkcheck.application.agent.dto.review.ReviewReport;
import com.ilink.test.ilinkcheck.application.agent.dto.review.ReviewSuggestion;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.model.*;
import com.ilink.test.ilinkcheck.application.agent.spi.rag.StyleGuideRetriever;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 审稿流程第二阶段。
 * 第一版先走规则型或 mock 型审稿，输出统一的审稿报告。
 */
@Service
public class ReviewGeneratePhaseExecutor implements PhaseExecutor {

    private final StyleGuideRetriever styleGuideRetriever;

    public ReviewGeneratePhaseExecutor(StyleGuideRetriever styleGuideRetriever) {
        this.styleGuideRetriever = styleGuideRetriever;
    }

    @Override
    public boolean supports(AgentTaskType taskType, AgentPhase phase) {
        return taskType == AgentTaskType.DOC_REVIEW && phase == AgentPhase.REVIEW_GENERATE;
    }

    @Override
    public PhaseExecutionResult execute(AgentState state) {
        String draftText = state.getArtifact(AgentArtifacts.REQUEST_DRAFT, String.class);
        List<ReviewSuggestion> suggestions = styleGuideRetriever.check(draftText);
        ReviewReport report = new ReviewReport(
                suggestions == null || suggestions.isEmpty(),
                suggestions == null ? List.of() : suggestions,
                suggestions == null || suggestions.isEmpty()
                        ? "本轮没有发现明显问题，建议人工再看一遍标题层级和段落节奏。"
                        : "本轮共发现 " + suggestions.size() + " 处建议修改项。"
        );

        String suggestionText = report.suggestions().isEmpty()
                ? "- 暂无具体修改项"
                : report.suggestions().stream()
                .map(item -> "- [" + item.type() + "] "
                        + defaultText(item.location(), "全文")
                        + "："
                        + item.message()
                        + "，建议："
                        + item.suggestion())
                .reduce((a, b) -> a + System.lineSeparator() + b)
                .orElse("- 暂无具体修改项");

        String reply = """
                技术文档审稿报告

                结论：
                %s

                具体建议：
                %s
                """.formatted(report.summary(), suggestionText).trim();

        return PhaseExecutionResult.finish(reply, Map.of(
                AgentArtifacts.REVIEW_SUGGESTIONS, suggestions,
                AgentArtifacts.REVIEW_REPORT, report
        ));
    }

    private String defaultText(String text, String fallback) {
        return text == null || text.isBlank() ? fallback : text.trim();
    }
}
