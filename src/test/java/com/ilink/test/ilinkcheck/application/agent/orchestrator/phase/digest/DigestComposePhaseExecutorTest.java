package com.ilink.test.ilinkcheck.application.agent.orchestrator.phase.digest;

import com.ilink.test.ilinkcheck.application.agent.dto.github.RepoInfo;
import com.ilink.test.ilinkcheck.application.agent.dto.github.TrendingRepo;
import com.ilink.test.ilinkcheck.application.agent.dto.roadmap.DevToArticle;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.model.AgentArtifacts;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.model.AgentPhase;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.model.AgentState;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.model.AgentTaskType;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.model.PhaseExecutionResult;
import com.ilink.test.ilinkcheck.application.agent.spi.mcp.DevToArticleProvider;
import com.ilink.test.ilinkcheck.application.agent.spi.mcp.GitHubRepoProvider;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DigestComposePhaseExecutorTest {

    @Test
    void shouldShowFallbackLabelWhenTrendingUsesTotalStars() {
        GitHubRepoProvider gitHubRepoProvider = new GitHubRepoProvider() {
            @Override
            public RepoInfo fetchRepo(String owner, String repo) {
                return null;
            }

            @Override
            public List<TrendingRepo> fetchTrending(String language, String since) {
                return List.of(
                        new TrendingRepo(
                                "spring-projects",
                                "spring-ai",
                                "Spring AI project",
                                "Java",
                                999,
                                "https://github.com/spring-projects/spring-ai",
                                TrendingRepo.MetricType.TOTAL_STARS,
                                TrendingRepo.Source.GITHUB_SEARCH_API
                        )
                );
            }
        };
        DevToArticleProvider devToArticleProvider = (tag, limit) -> List.of(
                new DevToArticle("AI article", "https://dev.to/example/ai", "ai", 100, "article")
        );

        DigestComposePhaseExecutor executor = new DigestComposePhaseExecutor(gitHubRepoProvider, devToArticleProvider);
        AgentState state = AgentState.newWorkflow("conv-digest", AgentTaskType.DAILY_DIGEST_SUBSCRIBE, AgentPhase.DIGEST_COMPOSE, "订阅晨报");
        state.putArtifact(AgentArtifacts.REQUEST_SCHEDULE_TIME, "08:30");

        PhaseExecutionResult result = executor.execute(state);

        assertThat(result.replyToUser()).contains("当前 Star: 999，官方回退");
    }
}
