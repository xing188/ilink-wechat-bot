package com.ilink.test.ilinkcheck.application.agent.orchestrator;

import com.ilink.test.ilinkcheck.application.agent.dto.KnowledgeItem;
import com.ilink.test.ilinkcheck.application.agent.dto.SentimentReport;
import com.ilink.test.ilinkcheck.application.agent.dto.WritingDraft;
import com.ilink.test.ilinkcheck.application.agent.spi.content.WriterAgent;
import com.ilink.test.ilinkcheck.application.agent.spi.rag.KnowledgeRetriever;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 草稿生成阶段：消费用户选题并生成最终草稿回复。
 */
@Service
public class DraftGeneratePhaseExecutor implements PhaseExecutor {

    private final WriterAgent writerAgent;
    private final KnowledgeRetriever knowledgeRetriever;

    public DraftGeneratePhaseExecutor(WriterAgent writerAgent, KnowledgeRetriever knowledgeRetriever) {
        this.writerAgent = writerAgent;
        this.knowledgeRetriever = knowledgeRetriever;
    }

    @Override
    public boolean supports(AgentTaskType taskType, AgentPhase phase) {
        return taskType == AgentTaskType.HOTSPOT_ANALYSIS && phase == AgentPhase.DRAFT_GENERATE;
    }

    @Override
    public PhaseExecutionResult execute(AgentState state) {
        String selectedTopic = state.getArtifact(AgentArtifacts.SELECTED_TOPIC, String.class);
        SentimentReport sentimentReport = state.getArtifact(AgentArtifacts.SENTIMENT_REPORT, SentimentReport.class);
        List<KnowledgeItem> knowledgeItems = knowledgeRetriever.search(selectedTopic);

        String platform = resolvePlatform(state.getOriginalUserMessage());
        String style = resolveStyle(sentimentReport, knowledgeItems);

        WritingDraft draft = writerAgent.write(selectedTopic, platform, style);
        String reply = buildDraftReply(draft, knowledgeItems);

        return PhaseExecutionResult.finish(reply, Map.of(
                AgentArtifacts.WRITING_DRAFT, draft,
                AgentArtifacts.KNOWLEDGE_ITEMS, knowledgeItems
        ));
    }

    private String resolvePlatform(String userMessage) {
        if (userMessage.contains("公众号")) {
            return "公众号";
        }
        if (userMessage.contains("知乎")) {
            return "知乎";
        }
        return "小红书";
    }

    private String resolveStyle(SentimentReport sentimentReport, List<KnowledgeItem> knowledgeItems) {
        if (knowledgeItems != null && !knowledgeItems.isEmpty()) {
            String style = knowledgeItems.getFirst().style();
            if (style != null && !style.isBlank()) {
                return style;
            }
        }

        if (sentimentReport == null) {
            return "理性拆解";
        }

        return Objects.equals(sentimentReport.overallSentiment(), "争议偏高")
                ? "理性拆解"
                : "情绪共鸣";
    }

    private String buildDraftReply(WritingDraft draft, List<KnowledgeItem> knowledgeItems) {
        String knowledgeHint = knowledgeItems == null || knowledgeItems.isEmpty()
                ? "本轮未命中额外 RAG 素材。"
                : "本轮额外参考了 %d 条 RAG 素材。".formatted(knowledgeItems.size());

        return """
                已根据你选择的方向生成第一版草稿：

                标题：%s
                平台：%s
                建议标签：%s
                质量分：%d

                正文：
                %s

                %s
                如果你想继续细化，可以直接说“改成公众号风格”或“再锐一点”。
                """.formatted(
                draft.title(),
                draft.platform(),
                String.join("、", draft.suggestedTags()),
                draft.qualityScore(),
                draft.content(),
                knowledgeHint
        ).trim();
    }
}
