package com.ilink.test.ilinkcheck.application.agent.dto.roadmap;

import java.util.List;

/**
 * 结构化学习路线计划。
 *
 * <p>每个阶段（预备知识 / 核心概念 / 实战建议）由 {@link RoadmapStep} 列表组成，
 * 每个步骤可携带标题 + 详细说明（预留字段，后续由 LLM CoT 或人工补充具体知识点）。</p>
 */
public record RoadmapPlan(
        String topic,
        List<RoadmapStep> prerequisites,
        List<RoadmapStep> coreConcepts,
        List<RoadmapStep> projectIdeas,
        List<DevToArticle> recommendedArticles
) {}
