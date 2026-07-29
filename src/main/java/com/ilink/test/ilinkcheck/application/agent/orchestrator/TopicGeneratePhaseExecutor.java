package com.ilink.test.ilinkcheck.application.agent.orchestrator;

import com.ilink.test.ilinkcheck.application.agent.dto.HotSearchResult;
import com.ilink.test.ilinkcheck.application.agent.dto.SentimentReport;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 选题生成阶段：基于热点和舆情，先给出 3 个可选方向。
 */
@Service
public class TopicGeneratePhaseExecutor implements PhaseExecutor {

    @Override
    public boolean supports(AgentTaskType taskType, AgentPhase phase) {
        return taskType == AgentTaskType.HOTSPOT_ANALYSIS && phase == AgentPhase.TOPIC_GENERATE;
    }

    @Override
    public PhaseExecutionResult execute(AgentState state) {
        List<HotSearchResult> hotSearchResults = state.getArtifact(AgentArtifacts.HOT_SEARCH_RESULTS);
        SentimentReport sentimentReport = state.getArtifact(AgentArtifacts.SENTIMENT_REPORT, SentimentReport.class);
        String keyword = state.getArtifact(AgentArtifacts.HOTSPOT_KEYWORD, String.class);

        List<String> topics = new ArrayList<>();
        String firstPainPoint = firstOrDefault(sentimentReport == null ? null : sentimentReport.painPoints(), "真实痛点");
        String firstControversy = firstOrDefault(sentimentReport == null ? null : sentimentReport.controversyPoints(), "争议焦点");
        String firstHotspot = firstOrDefault(hotSearchResults == null ? null : hotSearchResults.stream().map(HotSearchResult::title).toList(),
                keyword + " 热点");

        topics.add("【情绪共鸣型】" + firstHotspot + "：为什么大家都在讨论“" + firstPainPoint + "”？");
        topics.add("【观点拆解型】围绕“" + firstControversy + "”，普通人最容易忽略的 3 个判断点");
        topics.add("【行动建议型】面对“" + keyword + "”热议，普通人现在就能做的 5 个动作");

        return PhaseExecutionResult.next(AgentPhase.WAIT_USER_SELECTION, Map.of(
                AgentArtifacts.TOPIC_OPTIONS, List.copyOf(topics)
        ));
    }

    private String firstOrDefault(List<String> values, String defaultValue) {
        return values == null || values.isEmpty() ? defaultValue : values.getFirst();
    }
}
