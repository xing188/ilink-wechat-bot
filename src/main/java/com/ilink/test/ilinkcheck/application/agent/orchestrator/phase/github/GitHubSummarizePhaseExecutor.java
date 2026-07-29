package com.ilink.test.ilinkcheck.application.agent.orchestrator.phase.github;

import com.ilink.test.ilinkcheck.application.agent.dto.github.RepoInfo;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.model.*;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * GitHub 速读第二阶段。
 * 负责把仓库原始资料整理成用户可直接阅读的摘要回复。
 */
@Service
public class GitHubSummarizePhaseExecutor implements PhaseExecutor {

    @Override
    public boolean supports(AgentTaskType taskType, AgentPhase phase) {
        return taskType == AgentTaskType.GITHUB_REPO_DIGEST && phase == AgentPhase.GITHUB_SUMMARIZE;
    }

    @Override
    public PhaseExecutionResult execute(AgentState state) {
        RepoInfo repoInfo = state.getArtifact(AgentArtifacts.REPO_INFO, RepoInfo.class);
        if (repoInfo == null) {
            return PhaseExecutionResult.finish("仓库原始资料缺失，本次速读未能继续。", Map.of());
        }

        String reply = """
                GitHub 仓库速读：%s/%s

                一句话定位：
                %s

                主要语言：%s
                Star：%d
                Fork：%d
                技术栈：%s

                README 提炼：
                %s

                如果你愿意，我下一步可以继续帮你拆 README 结构或快速上手路径。
                """.formatted(
                repoInfo.owner(),
                repoInfo.name(),
                defaultText(repoInfo.description(), "这是一个值得继续阅读 README 的开源项目。"),
                defaultText(repoInfo.language(), "未知"),
                repoInfo.stars(),
                repoInfo.forks(),
                defaultText(repoInfo.techStack(), "暂无"),
                defaultText(repoInfo.readmeSummary(), "暂无摘要")
        ).trim();

        return PhaseExecutionResult.finish(reply, Map.of());
    }

    private String defaultText(String text, String fallback) {
        return text == null || text.isBlank() ? fallback : text.trim();
    }
}
