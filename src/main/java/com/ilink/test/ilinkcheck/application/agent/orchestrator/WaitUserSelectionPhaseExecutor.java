package com.ilink.test.ilinkcheck.application.agent.orchestrator;

import com.ilink.test.ilinkcheck.application.agent.dto.HotSearchResult;
import com.ilink.test.ilinkcheck.application.agent.dto.SentimentReport;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 等待用户选择阶段：第一次进入时发选项，下一次进入时消费用户回复。
 */
@Service
public class WaitUserSelectionPhaseExecutor implements PhaseExecutor {

    private static final Pattern OPTION_NUMBER = Pattern.compile("\\b([1-3])\\b");

    @Override
    public boolean supports(AgentTaskType taskType, AgentPhase phase) {
        return taskType == AgentTaskType.HOTSPOT_ANALYSIS && phase == AgentPhase.WAIT_USER_SELECTION;
    }

    @Override
    public PhaseExecutionResult execute(AgentState state) {
        Boolean promptSent = state.getArtifact(AgentArtifacts.SELECTION_PROMPT_SENT, Boolean.class);
        List<String> topics = state.getArtifact(AgentArtifacts.TOPIC_OPTIONS);
        if (topics == null || topics.isEmpty()) {
            return PhaseExecutionResult.finish("没有生成可选选题，请重新发起一次热点分析。", Map.of());
        }

        if (!Boolean.TRUE.equals(promptSent)) {
            String reply = buildSelectionPrompt(state, topics);
            return PhaseExecutionResult.waitForUser(AgentPhase.WAIT_USER_SELECTION, reply, Map.of(
                    AgentArtifacts.SELECTION_PROMPT_SENT, true
            ));
        }

        int selectedIndex = parseSelectionIndex(state.getLatestUserMessage(), topics);
        if (selectedIndex < 0) {
            return PhaseExecutionResult.waitForUser(
                    AgentPhase.WAIT_USER_SELECTION,
                    "请直接回复 1、2 或 3 选择一个选题；如果不想继续，可以回复“取消”。",
                    Map.of()
            );
        }

        return PhaseExecutionResult.next(AgentPhase.DRAFT_GENERATE, Map.of(
                AgentArtifacts.SELECTED_TOPIC, topics.get(selectedIndex)
        ));
    }

    private String buildSelectionPrompt(AgentState state, List<String> topics) {
        String keyword = state.getArtifact(AgentArtifacts.HOTSPOT_KEYWORD, String.class);
        SentimentReport sentimentReport = state.getArtifact(AgentArtifacts.SENTIMENT_REPORT, SentimentReport.class);
        List<HotSearchResult> hotSearchResults = state.getArtifact(AgentArtifacts.HOT_SEARCH_RESULTS);

        String hotspotTitle = hotSearchResults == null || hotSearchResults.isEmpty()
                ? keyword
                : hotSearchResults.getFirst().title();
        String sentiment = sentimentReport == null ? "暂无情绪判断" : sentimentReport.overallSentiment();

        return """
                已为你完成第一轮热点分析：
                - 关键词：%s
                - 核心热点：%s
                - 舆情判断：%s

                下面是 3 个可继续展开的选题，请直接回复数字：
                1. %s
                2. %s
                3. %s

                回复“取消”可结束本次 Agent 工作流。
                """.formatted(keyword, hotspotTitle, sentiment, topics.get(0), topics.get(1), topics.get(2)).trim();
    }

    private int parseSelectionIndex(String userMessage, List<String> topics) {
        if (!StringUtils.hasText(userMessage)) {
            return -1;
        }

        Matcher matcher = OPTION_NUMBER.matcher(userMessage.trim());
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1)) - 1;
        }

        for (int i = 0; i < topics.size(); i++) {
            if (topics.get(i).equals(userMessage.trim())) {
                return i;
            }
        }
        return -1;
    }
}
