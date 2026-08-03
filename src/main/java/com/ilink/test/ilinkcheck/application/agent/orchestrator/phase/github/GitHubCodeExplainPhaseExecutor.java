package com.ilink.test.ilinkcheck.application.agent.orchestrator.phase.github;

import com.ilink.test.ilinkcheck.application.agent.feature.github.GitHubRepoDigestService;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.model.AgentArtifacts;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.model.AgentPhase;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.model.AgentState;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.model.AgentTaskType;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.model.PhaseExecutionResult;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.model.PhaseExecutor;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.support.AgentTextUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * GitHub 仓库速读任务：【目录代码讲解阶段】
 * 前置阶段：GITHUB_DIRECTORY_EXPAND（已展示目录树，并询问用户想了解哪个目录的代码内容）
 *
 * <p>用户回复某个目录名（如 {@code java}）后，本阶段通过目录名→完整路径索引解析到真实路径，
 * 调用 {@link GitHubRepoDigestService#explainDirectoryCode} 对该目录代码进行讲解。</p>
 *
 * 归属任务：AgentTaskType.GITHUB_REPO_DIGEST
 * 绑定阶段：AgentPhase.GITHUB_CODE_EXPLAIN
 */
@Slf4j
@Service
public class GitHubCodeExplainPhaseExecutor implements PhaseExecutor {

    private final GitHubRepoDigestService gitHubRepoDigestService;

    public GitHubCodeExplainPhaseExecutor(GitHubRepoDigestService gitHubRepoDigestService) {
        this.gitHubRepoDigestService = gitHubRepoDigestService;
    }

    @Override
    public boolean supports(AgentTaskType taskType, AgentPhase phase) {
        return taskType == AgentTaskType.GITHUB_REPO_DIGEST && phase == AgentPhase.GITHUB_CODE_EXPLAIN;
    }

    @Override
    public PhaseExecutionResult execute(AgentState state) {
        String repoFull = state.getArtifact(AgentArtifacts.REQUEST_REPOSITORY, String.class);
        if (repoFull == null || !repoFull.contains("/")) {
            log.warn("conversationId={}, phase=GITHUB_CODE_EXPLAIN, repo artifact missing", state.getConversationId());
            return PhaseExecutionResult.finish("未找到仓库上下文，请重新发送仓库链接。", Map.of());
        }

        String userInput = AgentTextUtils.extractDirectoryPath(state.getLatestUserMessage()).orElse(null);
        if (userInput == null) {
            return PhaseExecutionResult.finish("请直接回复目录名，例如 `java`。回复「退出」结束浏览。", Map.of());
        }

        Map<String, String> pathIndex = getPathIndex(state);
        String dirPath = resolveDirectoryPath(userInput, pathIndex);

        String[] parts = repoFull.split("/", 2);
        String owner = parts[0];
        String repo = parts[1];

        try {
            String reply = gitHubRepoDigestService.explainDirectoryCode(owner, repo, dirPath);
            log.info("conversationId={}, phase=GITHUB_CODE_EXPLAIN, explain complete, dir={}",
                    state.getConversationId(), dirPath);
            return PhaseExecutionResult.finish(reply, Map.of());
        } catch (Exception e) {
            log.error("conversationId={}, phase=GITHUB_CODE_EXPLAIN, error, dir={}",
                    state.getConversationId(), dirPath, e);
            return PhaseExecutionResult.finish("讲解目录 `" + dirPath + "` 代码失败，请稍后再试。", Map.of());
        }
    }

    private String resolveDirectoryPath(String input, Map<String, String> pathIndex) {
        String normalized = input.replaceAll("/+$", "");
        if (StringUtils.hasText(normalized)) {
            String mapped = pathIndex.get(normalized);
            if (StringUtils.hasText(mapped)) {
                return mapped;
            }
        }
        return normalized;
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> getPathIndex(AgentState state) {
        Object index = state.getArtifact(AgentArtifacts.GITHUB_PATH_INDEX);
        Map<String, String> result = new HashMap<>();
        if (index instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    result.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
                }
            }
        }
        return result;
    }
}
