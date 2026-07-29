package com.ilink.test.ilinkcheck.application.agent.orchestrator.phase.github;

import com.ilink.test.ilinkcheck.application.agent.dto.github.RepoInfo;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.model.*;

import com.ilink.test.ilinkcheck.application.agent.orchestrator.support.AgentTextUtils;
import com.ilink.test.ilinkcheck.application.agent.spi.mcp.GitHubRepoProvider;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * GitHub 速读第一阶段。
 * 负责从用户输入中提取仓库信息，并通过 Provider 拿到仓库原始资料。
 */
@Service
public class GitHubFetchPhaseExecutor implements PhaseExecutor {

    private final GitHubRepoProvider gitHubRepoProvider;

    public GitHubFetchPhaseExecutor(GitHubRepoProvider gitHubRepoProvider) {
        this.gitHubRepoProvider = gitHubRepoProvider;
    }

    @Override
    public boolean supports(AgentTaskType taskType, AgentPhase phase) {
        return taskType == AgentTaskType.GITHUB_REPO_DIGEST && phase == AgentPhase.GITHUB_FETCH;
    }

    @Override
    public PhaseExecutionResult execute(AgentState state) {
        String[] repository = AgentTextUtils.extractRepository(state.getLatestUserMessage())
                .or(() -> AgentTextUtils.extractRepository(state.getOriginalUserMessage()))
                .orElse(null);

        if (repository == null) {
            return PhaseExecutionResult.waitForUser(
                    AgentPhase.GITHUB_FETCH,
                    "请直接发我 GitHub 仓库链接，或发 `owner/repo` 这样的仓库名。",
                    Map.of()
            );
        }

        RepoInfo repoInfo = gitHubRepoProvider.fetchRepo(repository[0], repository[1]);
        if (repoInfo == null) {
            return PhaseExecutionResult.finish("暂时没有获取到这个 GitHub 仓库的信息，请稍后再试。", Map.of());
        }

        return PhaseExecutionResult.next(AgentPhase.GITHUB_SUMMARIZE, Map.of(
                AgentArtifacts.REQUEST_REPOSITORY, repository[0] + "/" + repository[1],
                AgentArtifacts.REPO_INFO, repoInfo
        ));
    }
}
