package com.ilink.test.ilinkcheck.application.agent.orchestrator.phase.digest;

import com.ilink.test.ilinkcheck.application.agent.dto.digest.DigestContent;
import com.ilink.test.ilinkcheck.application.agent.dto.github.TrendingRepo;
import com.ilink.test.ilinkcheck.application.agent.dto.roadmap.DevToArticle;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.model.*;

import com.ilink.test.ilinkcheck.application.agent.spi.mcp.DevToArticleProvider;
import com.ilink.test.ilinkcheck.application.agent.spi.mcp.GitHubRepoProvider;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 晨报流程第二阶段。
 * 当前阶段先输出订阅确认和晨报预览；真正的定时推送由后续 scheduler 接管。
 */
@Service
public class DigestComposePhaseExecutor implements PhaseExecutor {

    private final GitHubRepoProvider gitHubRepoProvider;
    private final DevToArticleProvider devToArticleProvider;

    public DigestComposePhaseExecutor(GitHubRepoProvider gitHubRepoProvider,
                                      DevToArticleProvider devToArticleProvider) {
        this.gitHubRepoProvider = gitHubRepoProvider;
        this.devToArticleProvider = devToArticleProvider;
    }

    @Override
    public boolean supports(AgentTaskType taskType, AgentPhase phase) {
        return taskType == AgentTaskType.DAILY_DIGEST_SUBSCRIBE && phase == AgentPhase.DIGEST_COMPOSE;
    }

    @Override
    public PhaseExecutionResult execute(AgentState state) {
        String scheduleTime = state.getArtifact(AgentArtifacts.REQUEST_SCHEDULE_TIME, String.class);
        List<TrendingRepo> trendingRepos = gitHubRepoProvider.fetchTrending("java", "daily");
        List<DevToArticle> hotArticles = devToArticleProvider.fetchByTag("ai", 3);
        DigestContent digestContent = new DigestContent(
                trendingRepos == null ? List.of() : trendingRepos,
                hotArticles == null ? List.of() : hotArticles,
                LocalDate.now().toString()
        );

        String repoText = digestContent.trendingRepos().isEmpty()
                ? "- 暂无 GitHub 热点"
                : digestContent.trendingRepos().stream()
                .map(item -> "- " + item.owner() + "/" + item.name()
                        + "（" + item.formattedStarMetric() + (item.isFallbackMetric() ? "，官方回退" : "") + "）："
                        + defaultText(item.description(), "暂无描述"))
                .reduce((a, b) -> a + System.lineSeparator() + b)
                .orElse("- 暂无 GitHub 热点");

        String articleText = digestContent.hotArticles().isEmpty()
                ? "- 暂无技术文章"
                : digestContent.hotArticles().stream()
                .map(item -> "- " + item.title() + "："
                        + defaultText(item.description(), "暂无描述"))
                .reduce((a, b) -> a + System.lineSeparator() + b)
                .orElse("- 暂无技术文章");

        String reply = """
                已为你记录技术晨报订阅时间：%s

                晨报预览（%s）
                GitHub 热点：
                %s

                技术文章：
                %s

                下一步如果要真正定时推送，把这份订阅结果落到数据库或调度器即可。
                """.formatted(scheduleTime, digestContent.date(), repoText, articleText).trim();

        return PhaseExecutionResult.finish(reply, Map.of(
                AgentArtifacts.DIGEST_CONTENT, digestContent
        ));
    }

    private String defaultText(String text, String fallback) {
        return text == null || text.isBlank() ? fallback : text.trim();
    }
}
