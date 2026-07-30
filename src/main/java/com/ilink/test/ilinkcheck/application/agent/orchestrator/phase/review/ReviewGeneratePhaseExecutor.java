package com.ilink.test.ilinkcheck.application.agent.orchestrator.phase.review;

import com.ilink.test.ilinkcheck.application.agent.dto.review.ReviewReport;
import com.ilink.test.ilinkcheck.application.agent.dto.review.ReviewSuggestion;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.model.*;
import com.ilink.test.ilinkcheck.application.agent.spi.rag.StyleGuideRetriever;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 文档审稿任务：第二阶段【生成审稿报告阶段】
 * 前置阶段：REVIEW_COLLECT_DRAFT
 * 职责：
 * 1. 从会话工件读取用户提交的文档草稿
 * 2. 调用RAG风格规范检索器，依据规范检测文本问题
 * 3. 组装结构化审稿报告，渲染可读文本返回用户
 * 4. 将建议列表、报告存入会话工件，结束整条工作流
 *
 * 归属任务：AgentTaskType.DOC_REVIEW
 * 绑定阶段：AgentPhase.REVIEW_GENERATE
 */
@Slf4j
@Service
public class ReviewGeneratePhaseExecutor implements PhaseExecutor {

    private static final String MSG_DRAFT_MISS = "未获取到待审核文档草稿，审稿流程终止。";

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
        String conversationId = state.getConversationId();
        String draftText = state.getArtifact(AgentArtifacts.REQUEST_DRAFT, String.class);

        if (!org.springframework.util.StringUtils.hasText(draftText)) {
            log.warn("conversationId={}, phase=REVIEW_GENERATE, draft artifact missing", conversationId);
            return PhaseExecutionResult.finish(MSG_DRAFT_MISS, Map.of());
        }

        List<ReviewSuggestion> suggestions;
        try {
            suggestions = styleGuideRetriever.check(draftText);
        } catch (Exception e) {
            log.error("conversationId={}, phase=REVIEW_GENERATE, rag style check error", conversationId, e);
            return PhaseExecutionResult.finish("文档规范校验服务异常，暂时无法完成审稿，请稍后重试。", Map.of());
        }

        // 统一归一化，消除null，后续代码无需重复判空
        List<ReviewSuggestion> suggestionList = suggestions == null ? new ArrayList<>() : suggestions;
        log.info("conversationId={}, phase=REVIEW_GENERATE, check complete, suggestionCount={}",
                conversationId, suggestionList.size());

        String summaryText = suggestionList.isEmpty()
                ? "本轮没有发现明显问题，建议人工再看一遍标题层级和段落节奏。"
                : "本轮共发现 " + suggestionList.size() + " 处建议修改项。";

        // 使用collect拼接，相比reduce更稳定
        String suggestionText = suggestionList.stream()
                .map(item -> "- [" + item.type() + "] "
                        + defaultText(item.location(), "全文")
                        + "："
                        + item.message()
                        + "，建议："
                        + item.suggestion())
                .collect(Collectors.joining(System.lineSeparator(), "", ""));
        if (suggestionText.isBlank()) {
            suggestionText = "- 暂无具体修改项";
        }

        ReviewReport report = new ReviewReport(
                suggestionList.isEmpty(),
                suggestionList,
                summaryText
        );

        String reply = """
                技术文档审稿报告

                结论：
                %s

                具体建议：
                %s
                """.formatted(report.summary(), suggestionText).trim();

        log.info("conversationId={}, phase=REVIEW_GENERATE, review workflow finish", conversationId);
        return PhaseExecutionResult.finish(reply, Map.of(
                AgentArtifacts.REVIEW_SUGGESTIONS, suggestionList,
                AgentArtifacts.REVIEW_REPORT, report
        ));
    }

    /**
     * 字符串空值兜底工具
     */
    private String defaultText(String text, String fallback) {
        return text == null || text.isBlank() ? fallback : text.trim();
    }
}