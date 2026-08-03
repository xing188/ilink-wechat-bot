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
 * GitHub 仓库速读任务：【目录树展开阶段】
 * 前置阶段：GITHUB_DIRECTORY_ANALYZE（已输出核心目录架构，提示用户可回复目录名继续）
 *
 * <p>用户回复目录名（如 {@code main}）后，本阶段把该目录的完整子目录树一次性展示出来，
 * 并询问用户想了解哪个目录的代码内容；随后流转至 {@link AgentPhase#GITHUB_CODE_EXPLAIN}。</p>
 *
 * 归属任务：AgentTaskType.GITHUB_REPO_DIGEST
 * 绑定阶段：AgentPhase.GITHUB_DIRECTORY_EXPAND
 */
@Slf4j
@Service
public class GitHubDirectoryExpandPhaseExecutor implements PhaseExecutor {

    /** 目录树最大展开深度（选中目录起向下）。 */
    private static final int TREE_MAX_DEPTH = 3;

    private final GitHubRepoDigestService gitHubRepoDigestService;

    public GitHubDirectoryExpandPhaseExecutor(GitHubRepoDigestService gitHubRepoDigestService) {
        this.gitHubRepoDigestService = gitHubRepoDigestService;
    }

    @Override
    public boolean supports(AgentTaskType taskType, AgentPhase phase) {
        return taskType == AgentTaskType.GITHUB_REPO_DIGEST && phase == AgentPhase.GITHUB_DIRECTORY_EXPAND;
    }

    @Override
    public PhaseExecutionResult execute(AgentState state) {
        String repoFull = state.getArtifact(AgentArtifacts.REQUEST_REPOSITORY, String.class);
        if (repoFull == null || !repoFull.contains("/")) {
            log.warn("conversationId={}, phase=GITHUB_DIRECTORY_EXPAND, repo artifact missing", state.getConversationId());
            return PhaseExecutionResult.finish("未找到仓库上下文，请重新发送仓库链接。", Map.of());
        }

        String userInput = AgentTextUtils.extractDirectoryPath(state.getLatestUserMessage()).orElse(null);
        if (userInput == null) {
            return PhaseExecutionResult.finish("请直接回复目录名，例如 `main`、`src/main`。回复「退出」结束浏览。", Map.of());
        }

        // 用「目录名→完整路径」索引解析短目录名（main → src/main）
        Map<String, String> pathIndex = getPathIndex(state);
        String dirPath = resolveDirectoryPath(userInput, pathIndex);

        String[] parts = repoFull.split("/", 2);
        String owner = parts[0];
        String repo = parts[1];

        try {
            GitHubRepoDigestService.DirectoryTreeView tree =
                    gitHubRepoDigestService.buildDeepDirectoryTree(owner, repo, dirPath, TREE_MAX_DEPTH);
            // 把新展开的目录路径补进索引（深目录名 → 完整路径），供代码讲解阶段按名字定位
            Map<String, String> mergedIndex = new HashMap<>(pathIndex);
            mergedIndex.putAll(tree.pathIndex());

            String reply = tree.text() + "\n\n👆 想了解哪个目录的代码内容？直接回复目录名（如 java），我来讲解该目录代码；回复「退出」结束浏览。";
            log.info("conversationId={}, phase=GITHUB_DIRECTORY_EXPAND, tree shown, dir={}",
                    state.getConversationId(), dirPath);
            return PhaseExecutionResult.waitForUser(
                    AgentPhase.GITHUB_CODE_EXPLAIN,
                    reply,
                    Map.of(AgentArtifacts.GITHUB_PATH_INDEX, mergedIndex)
            );
        } catch (Exception e) {
            log.error("conversationId={}, phase=GITHUB_DIRECTORY_EXPAND, error, dir={}",
                    state.getConversationId(), dirPath, e);
            return PhaseExecutionResult.finish("获取目录 `" + dirPath + "` 内容失败，请确认目录名是否正确，或回复「退出」结束浏览。", Map.of());
        }
    }

    /**
     * 优先用索引把短目录名解析成完整路径；索引未命中时按原样使用（兼容直接输入完整路径）。
     */
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
