package com.ilink.test.ilinkcheck.application.agent.orchestrator.phase.roadmap;

import com.ilink.test.ilinkcheck.application.agent.dto.roadmap.DevToArticle;
import com.ilink.test.ilinkcheck.application.agent.dto.roadmap.RoadmapPlan;
import com.ilink.test.ilinkcheck.application.agent.dto.roadmap.SearchResult;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.model.*;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 学习路线第二阶段。
 * 负责把外部参考资料整理成结构化学习路线。
 */
@Service
public class RoadmapComposePhaseExecutor implements PhaseExecutor {

    @Override
    public boolean supports(AgentTaskType taskType, AgentPhase phase) {
        return taskType == AgentTaskType.TECH_ROADMAP && phase == AgentPhase.ROADMAP_COMPOSE;
    }

    @Override
    public PhaseExecutionResult execute(AgentState state) {
        String topic = state.getArtifact(AgentArtifacts.REQUEST_TOPIC, String.class);
        List<DevToArticle> articles = state.getArtifact(AgentArtifacts.ROADMAP_ARTICLES);
        List<SearchResult> references = state.getArtifact(AgentArtifacts.ROADMAP_REFERENCES);

        RoadmapPlan plan = new RoadmapPlan(
                topic,
                List.of("基础编程语法", "命令行与调试能力", "阅读英文文档的基本能力"),
                List.of(topic + " 的核心概念", topic + " 的运行机制", topic + " 的常见约束"),
                List.of("做一个最小 demo", "完成一个可复现实践", "整理一篇学习笔记"),
                articles == null ? List.of() : articles
        );

        String articleText = (articles == null || articles.isEmpty())
                ? "- 暂无 Dev.to 推荐文章"
                : articles.stream()
                .map(article -> "- " + article.title() + "："
                        + defaultText(article.description(), "暂无摘要")
                        + "（👍 " + article.positiveReactions() + "）")
                .reduce((a, b) -> a + System.lineSeparator() + b)
                .orElse("- 暂无 Dev.to 推荐文章");

        String referenceText = (references == null || references.isEmpty())
                ? "- 暂无补充检索结果"
                : references.stream()
                .map(result -> "- " + result.title() + "："
                        + defaultText(result.snippet(), "暂无摘要"))
                .reduce((a, b) -> a + System.lineSeparator() + b)
                .orElse("- 暂无补充检索结果");

        String reply = """
                %s 学习路线

                第一阶段：预备知识
                - %s

                第二阶段：核心概念
                - %s

                第三阶段：实战建议
                - %s

                推荐资料
                %s

                补充参考
                %s
                """.formatted(
                plan.topic(),
                String.join(System.lineSeparator() + "- ", plan.prerequisites()),
                String.join(System.lineSeparator() + "- ", plan.coreConcepts()),
                String.join(System.lineSeparator() + "- ", plan.projectIdeas()),
                articleText,
                referenceText
        ).trim();

        return PhaseExecutionResult.finish(reply, Map.of(
                AgentArtifacts.ROADMAP_PLAN, plan
        ));
    }

    private String defaultText(String text, String fallback) {
        return text == null || text.isBlank() ? fallback : text.trim();
    }
}
