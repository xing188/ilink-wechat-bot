package com.ilink.test.ilinkcheck.application.agent.orchestrator.phase.roadmap;

import com.ilink.test.ilinkcheck.application.agent.dto.roadmap.DevToArticle;
import com.ilink.test.ilinkcheck.application.agent.dto.roadmap.SearchResult;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.model.*;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.support.AgentTextUtils;
import com.ilink.test.ilinkcheck.application.agent.spi.mcp.DevToArticleProvider;
import com.ilink.test.ilinkcheck.application.agent.spi.mcp.WebSearchProvider;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 学习路线第一阶段。
 * 先提取学习主题，再拉取 Dev.to 文章和 WebSearch 参考资料。
 */
@Service
public class RoadmapFetchPhaseExecutor implements PhaseExecutor {

    private final DevToArticleProvider devToArticleProvider;
    private final WebSearchProvider webSearchProvider;

    public RoadmapFetchPhaseExecutor(DevToArticleProvider devToArticleProvider,
                                     WebSearchProvider webSearchProvider) {
        this.devToArticleProvider = devToArticleProvider;
        this.webSearchProvider = webSearchProvider;
    }

    @Override
    public boolean supports(AgentTaskType taskType, AgentPhase phase) {
        return taskType == AgentTaskType.TECH_ROADMAP && phase == AgentPhase.ROADMAP_FETCH;
    }

    @Override
    public PhaseExecutionResult execute(AgentState state) {
        String topic = AgentTextUtils.extractTopic(state.getLatestUserMessage())
                .or(() -> AgentTextUtils.extractTopic(state.getOriginalUserMessage()))
                .orElse(null);

        if (topic == null) {
            return PhaseExecutionResult.waitForUser(
                    AgentPhase.ROADMAP_FETCH,
                    "你想学什么技术？直接回复一个主题，比如 `eBPF`、`RAG` 或 `Spring AI`。",
                    Map.of()
            );
        }

        // 是否要求把资料翻译成中文（如"我想学 RAG 翻译成中"）
        boolean translate = AgentTextUtils.wantsChinese(state.getLatestUserMessage())
                || AgentTextUtils.wantsChinese(state.getOriginalUserMessage());

        // 注意：保留主题原始大小写（如 eBPF），大小写归一化交给 Provider 实现决定（Dev.to 标签本身大小写不敏感）。
        List<DevToArticle> articles = devToArticleProvider.fetchByTag(topic, 3);
        List<SearchResult> references = webSearchProvider.search(topic + " tutorial", 2);

        return PhaseExecutionResult.next(AgentPhase.ROADMAP_COMPOSE, Map.of(
                AgentArtifacts.REQUEST_TOPIC, topic,
                AgentArtifacts.ROADMAP_ARTICLES, articles,
                AgentArtifacts.ROADMAP_REFERENCES, references,
                AgentArtifacts.ROADMAP_TRANSLATE, translate
        ));
    }
}
