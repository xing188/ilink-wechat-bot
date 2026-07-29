package com.ilink.test.ilinkcheck.application.agent.orchestrator;

import com.ilink.test.ilinkcheck.application.agent.dto.HotSearchResult;
import com.ilink.test.ilinkcheck.application.agent.dto.SentimentReport;
import com.ilink.test.ilinkcheck.application.agent.spi.content.SentimentAnalyzer;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 舆情分析阶段：把热点列表压缩成一个可供后续选题使用的情绪画像。
 */
@Service
public class SentimentAnalyzePhaseExecutor implements PhaseExecutor {

    private final SentimentAnalyzer sentimentAnalyzer;

    public SentimentAnalyzePhaseExecutor(SentimentAnalyzer sentimentAnalyzer) {
        this.sentimentAnalyzer = sentimentAnalyzer;
    }

    @Override
    public boolean supports(AgentTaskType taskType, AgentPhase phase) {
        return taskType == AgentTaskType.HOTSPOT_ANALYSIS && phase == AgentPhase.SENTIMENT_ANALYZE;
    }

    @Override
    public PhaseExecutionResult execute(AgentState state) {
        List<HotSearchResult> hotSearchResults = state.getArtifact(AgentArtifacts.HOT_SEARCH_RESULTS);
        String mergedText = hotSearchResults == null ? "" : hotSearchResults.stream()
                .map(item -> item.title() + " " + Objects.toString(item.summary(), ""))
                .collect(Collectors.joining("；"));

        SentimentReport report = sentimentAnalyzer.analyze(mergedText);
        return PhaseExecutionResult.next(AgentPhase.TOPIC_GENERATE, Map.of(
                AgentArtifacts.SENTIMENT_REPORT, report
        ));
    }
}
