package com.ilink.test.ilinkcheck.application.agent.orchestrator;

import com.ilink.test.ilinkcheck.application.agent.dto.HotSearchResult;
import com.ilink.test.ilinkcheck.application.agent.spi.mcp.HotSearchProvider;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 热点抓取阶段：负责从 Provider 拉回原始热点。
 */
@Service
public class HotspotFetchPhaseExecutor implements PhaseExecutor {

    private static final Pattern QUOTED_KEYWORD = Pattern.compile("[“\"']([^”\"']+)[”\"']");
    private static final Pattern ABOUT_KEYWORD = Pattern.compile("(?:关于|围绕)(.+?)(?:的)?(?:热点|热搜|舆情|选题)");

    private final HotSearchProvider hotSearchProvider;

    public HotspotFetchPhaseExecutor(HotSearchProvider hotSearchProvider) {
        this.hotSearchProvider = hotSearchProvider;
    }

    @Override
    public boolean supports(AgentTaskType taskType, AgentPhase phase) {
        return taskType == AgentTaskType.HOTSPOT_ANALYSIS && phase == AgentPhase.HOTSPOT_FETCH;
    }

    @Override
    public PhaseExecutionResult execute(AgentState state) {
        String keyword = extractKeyword(state.getLatestUserMessage());
        List<HotSearchResult> results = hotSearchProvider.searchByKeyword(keyword);
        if (results == null || results.isEmpty()) {
            results = hotSearchProvider.fetchHotSearch("weibo");
        }

        List<HotSearchResult> topResults = results == null ? List.of() : results.stream().limit(3).toList();
        if (topResults.isEmpty()) {
            return PhaseExecutionResult.finish("暂时没有检索到可用热点，请稍后重试。", Map.of());
        }

        return PhaseExecutionResult.next(AgentPhase.SENTIMENT_ANALYZE, Map.of(
                AgentArtifacts.HOTSPOT_KEYWORD, keyword,
                AgentArtifacts.HOT_SEARCH_RESULTS, topResults
        ));
    }

    private String extractKeyword(String userMessage) {
        Matcher quotedMatcher = QUOTED_KEYWORD.matcher(userMessage);
        if (quotedMatcher.find()) {
            return quotedMatcher.group(1).trim();
        }

        Matcher aboutMatcher = ABOUT_KEYWORD.matcher(userMessage);
        if (aboutMatcher.find()) {
            return aboutMatcher.group(1).trim();
        }

        if (userMessage.contains("打工人")) {
            return "打工人";
        }
        if (userMessage.contains("AI")) {
            return "AI";
        }
        return "职场";
    }
}
