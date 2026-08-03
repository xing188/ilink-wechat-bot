package com.ilink.test.ilinkcheck.application.agent.orchestrator.phase.roadmap;

import com.ilink.test.ilinkcheck.application.agent.dto.roadmap.DevToArticle;
import com.ilink.test.ilinkcheck.application.agent.dto.roadmap.RoadmapPlan;
import com.ilink.test.ilinkcheck.application.agent.dto.roadmap.SearchResult;
import com.ilink.test.ilinkcheck.application.agent.feature.roadmap.RoadmapComposeService;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.model.*;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 学习路线第二阶段。
 * 从共享上下文取出主题与资料，调用 {@link RoadmapComposeService} 生成结构化路线并渲染成回复。
 */
@Service
public class RoadmapComposePhaseExecutor implements PhaseExecutor {

    private final RoadmapComposeService composeService;

    public RoadmapComposePhaseExecutor(RoadmapComposeService composeService) {
        this.composeService = composeService;
    }

    @Override
    public boolean supports(AgentTaskType taskType, AgentPhase phase) {
        return taskType == AgentTaskType.TECH_ROADMAP && phase == AgentPhase.ROADMAP_COMPOSE;
    }

    @Override
    public PhaseExecutionResult execute(AgentState state) {
        String topic = state.getArtifact(AgentArtifacts.REQUEST_TOPIC, String.class);
        List<DevToArticle> articles = state.getArtifact(AgentArtifacts.ROADMAP_ARTICLES);
        List<SearchResult> references = state.getArtifact(AgentArtifacts.ROADMAP_REFERENCES);
        boolean translate = Boolean.TRUE.equals(state.getArtifact(AgentArtifacts.ROADMAP_TRANSLATE, Boolean.class));

        // 渐近式交互：先给一份精简版（仅阶段 + 步骤标题），再询问用户是否需要
        // 带说明与资料链接的完整版；完整版由 ROADMAP_CONFIRM_DETAIL 阶段按需渲染。
        // translate 标记一并存入上下文，供确认阶段判断是否要中文版。
        RoadmapPlan plan = composeService.compose(topic, articles, references);
        String concise = composeService.renderConcise(plan);

        return PhaseExecutionResult.waitForUser(
                AgentPhase.ROADMAP_CONFIRM_DETAIL,
                concise,
                Map.of(
                        AgentArtifacts.ROADMAP_PLAN, plan,
                        AgentArtifacts.ROADMAP_ARTICLES, articles,
                        AgentArtifacts.ROADMAP_REFERENCES, references,
                        AgentArtifacts.ROADMAP_TRANSLATE, translate
                )
        );
    }
}
