package com.ilink.test.ilinkcheck.application.agent.orchestrator.phase.github;

import com.ilink.test.ilinkcheck.application.agent.dto.github.RepoInfo;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.model.*;

import com.ilink.test.ilinkcheck.application.agent.orchestrator.support.AgentTextUtils;
import com.ilink.test.ilinkcheck.application.agent.spi.mcp.GitHubRepoProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

/**
 * GitHub仓库速读任务：第一阶段【仓库信息拉取阶段】
 * 职责：
 * 1. 从用户对话文本提取仓库标识（链接 / owner/repo）
 * 2. 调用GitHub MCP（Provider）客户端拉取仓库基础元数据
 * 3. 提取失败则暂停流程，引导用户输入仓库地址
 * 4. 数据获取成功后，产出工件并流转至摘要阶段 GitHub_SUMMARIZE
 * 归属任务：AgentTaskType.GITHUB_REPO_DIGEST
 * 绑定阶段：AgentPhase.GITHUB_FETCH
 */
@Slf4j
@Service
public class GitHubFetchPhaseExecutor implements PhaseExecutor {

    private static final String PROMPT_NEED_REPO = "请直接发我 GitHub 仓库链接，或发 `owner/repo` 这样的仓库名。";
    private static final String PROMPT_FETCH_FAIL = "暂时没有获取到这个 GitHub 仓库的信息，请确认仓库地址正确或稍后再试。";

    private final GitHubRepoProvider gitHubRepoProvider;

    public GitHubFetchPhaseExecutor(GitHubRepoProvider gitHubRepoProvider) {
        this.gitHubRepoProvider = gitHubRepoProvider;
    }

    /**
     * 声明当前执行器负责的任务类型与阶段
     */
    @Override
    public boolean supports(AgentTaskType taskType, AgentPhase phase) {
        return taskType == AgentTaskType.GITHUB_REPO_DIGEST && phase == AgentPhase.GITHUB_FETCH;
    }
    @Override
    public PhaseExecutionResult execute(AgentState state) {
        String conversationId = state.getConversationId();
        // 优先使用本轮用户最新消息，兜底使用任务启动原始消息（流程恢复场景兼容）
        Optional<String[]> repoOpt = AgentTextUtils.extractRepository(state.getLatestUserMessage())
                .or(() -> AgentTextUtils.extractRepository(state.getOriginalUserMessage()));

        if (repoOpt.isEmpty()) {
            log.info("conversationId={}, phase=GITHUB_FETCH, result=need_user_input", conversationId);
            // 保持当前阶段，等待用户提供仓库地址
            return PhaseExecutionResult.waitForUser(
                    AgentPhase.GITHUB_FETCH,
                    PROMPT_NEED_REPO,
                    Map.of()
            );
        }

        String[] repository = repoOpt.get();
        String owner = repository[0];
        String repoName = repository[1];
        log.info("conversationId={}, phase=GITHUB_FETCH, targetRepo={}/{}", conversationId, owner, repoName);

        RepoInfo repoInfo;
        try {
            repoInfo = gitHubRepoProvider.fetchRepo(owner, repoName);
        } catch (Exception e) {
            log.error("conversationId={}, phase=GITHUB_FETCH, mcp_fetch_exception repo={}/{}",
                    conversationId, owner, repoName, e);
            return PhaseExecutionResult.finish(PROMPT_FETCH_FAIL, Map.of());
        }

        if (repoInfo == null) {
            log.warn("conversationId={}, phase=GITHUB_FETCH, repo_not_found repo={}/{}",
                    conversationId, owner, repoName);
            return PhaseExecutionResult.finish(PROMPT_FETCH_FAIL, Map.of());
        }

        log.info("conversationId={}, phase=GITHUB_FETCH, fetch_success, nextPhase=GITHUB_SUMMARIZE", conversationId);
        // 产出工件，流转至摘要阶段
        return PhaseExecutionResult.next(AgentPhase.GITHUB_SUMMARIZE, Map.of(
                AgentArtifacts.REQUEST_REPOSITORY, owner + "/" + repoName,
                AgentArtifacts.REPO_INFO, repoInfo,
                AgentArtifacts.README_RAW, repoInfo.readmeRaw() != null ? repoInfo.readmeRaw() : ""
        ));
    }
}