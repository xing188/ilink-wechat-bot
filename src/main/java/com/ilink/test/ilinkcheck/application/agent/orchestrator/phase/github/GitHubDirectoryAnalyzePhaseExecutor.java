package com.ilink.test.ilinkcheck.application.agent.orchestrator.phase.github;

import com.ilink.test.ilinkcheck.application.agent.dto.github.RepoTreeNode;
import com.ilink.test.ilinkcheck.application.agent.feature.github.GitHubRepoDigestService;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.model.AgentArtifacts;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.model.AgentPhase;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.model.AgentState;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.model.AgentTaskType;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.model.PhaseExecutionResult;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.model.PhaseExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * GitHub 仓库速读任务：【目录架构分析阶段】
 * 前置阶段：GITHUB_SUMMARIZE（速读摘要已输出，并询问用户是否需要分析目录架构）
 *
 * <p>职责：根据用户的回复，确认时调用 {@link GitHubRepoDigestService} 拉取根目录树
 * 并生成核心目录架构分析；拒绝时结束流程。</p>
 *
 * 归属任务：AgentTaskType.GITHUB_REPO_DIGEST
 * 绑定阶段：AgentPhase.GITHUB_DIRECTORY_ANALYZE
 */
@Slf4j
@Service
public class GitHubDirectoryAnalyzePhaseExecutor implements PhaseExecutor {

    private static final List<String> CONFIRM_WORDS = List.of(
            "好", "可以", "要", "看代码", "查看", "分析", "确认", "yes"
    );
    private static final List<String> REJECT_WORDS = List.of(
            "不用", "不需要", "算了", "不要", "no", "不了", "跳过"
    );

    private final GitHubRepoDigestService gitHubRepoDigestService;

    public GitHubDirectoryAnalyzePhaseExecutor(GitHubRepoDigestService gitHubRepoDigestService) {
        this.gitHubRepoDigestService = gitHubRepoDigestService;
    }

    @Override
    public boolean supports(AgentTaskType taskType, AgentPhase phase) {
        return taskType == AgentTaskType.GITHUB_REPO_DIGEST && phase == AgentPhase.GITHUB_DIRECTORY_ANALYZE;
    }

    @Override
    public PhaseExecutionResult execute(AgentState state) {
        String latest = state.getLatestUserMessage();

        // 用户拒绝：跳过目录分析
        if (containsAny(latest, REJECT_WORDS)) {
            log.info("conversationId={}, phase=GITHUB_DIRECTORY_ANALYZE, result=rejected", state.getConversationId());
            return PhaseExecutionResult.finish("好的，已跳过目录架构分析。需要时回复「看代码」即可。", Map.of());
        }

        // 非确认也非拒绝：继续等待明确回复
        if (!containsAny(latest, CONFIRM_WORDS)) {
            return PhaseExecutionResult.waitForUser(
                    AgentPhase.GITHUB_DIRECTORY_ANALYZE,
                    "需要我分析这个仓库的核心目录架构吗？回复「好」查看，回复「不用」跳过。",
                    Map.of()
            );
        }

        // 用户确认：从工件读取仓库信息并执行目录分析
        String repoFull = state.getArtifact(AgentArtifacts.REQUEST_REPOSITORY, String.class);
        if (repoFull == null || !repoFull.contains("/")) {
            log.warn("conversationId={}, phase=GITHUB_DIRECTORY_ANALYZE, repo artifact missing", state.getConversationId());
            return PhaseExecutionResult.finish("未找到待分析的仓库信息，请重新发送仓库链接。", Map.of());
        }

        String[] parts = repoFull.split("/", 2);
        String owner = parts[0];
        String repo = parts[1];

        try {
            List<RepoTreeNode> nodes = gitHubRepoDigestService.fetchRootDirectory(owner, repo);
            String reply = gitHubRepoDigestService.buildDirectoryArchitecture(owner, repo, nodes);
            // 构建"目录名→完整路径"索引，供后续回复目录名继续展开时解析路径
            Map<String, String> pathIndex = gitHubRepoDigestService.buildPathIndex(owner, repo, nodes);
            log.info("conversationId={}, phase=GITHUB_DIRECTORY_ANALYZE, directory analysis complete",
                    state.getConversationId());
            return PhaseExecutionResult.finish(reply, Map.of(
                    AgentArtifacts.GITHUB_PATH_INDEX, pathIndex
            ));
        } catch (Exception e) {
            log.error("conversationId={}, phase=GITHUB_DIRECTORY_ANALYZE, error",
                    state.getConversationId(), e);
            return PhaseExecutionResult.finish("获取目录架构失败，请稍后再试。", Map.of());
        }
    }

    private boolean containsAny(String text, List<String> candidates) {
        if (text == null) {
            return false;
        }
        return candidates.stream().anyMatch(text::contains);
    }
}
