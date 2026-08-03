package com.ilink.test.ilinkcheck.application.agent.orchestrator.phase.github;

import com.ilink.test.ilinkcheck.application.agent.dto.github.RepoInfo;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * GitHub 速读第二阶段。
 * 负责把仓库原始资料整理成用户可直接阅读的摘要回复。
 */
@Slf4j
@Service
public class GitHubSummarizePhaseExecutor implements PhaseExecutor {

    private static final String MSG_MISS_REPO_DATA = "仓库原始资料缺失，本次速读未能继续。";

    @Override
    public boolean supports(AgentTaskType taskType, AgentPhase phase) {
        return taskType == AgentTaskType.GITHUB_REPO_DIGEST && phase == AgentPhase.GITHUB_SUMMARIZE;
    }

    @Override
    public PhaseExecutionResult execute(AgentState state) {
        String conversationId = state.getConversationId();
        RepoInfo repoInfo = state.getArtifact(AgentArtifacts.REPO_INFO, RepoInfo.class);

        if (repoInfo == null) {
            log.warn("conversationId={}, phase=GITHUB_SUMMARIZE, artifact REPO_INFO missing", conversationId);
            return PhaseExecutionResult.finish(MSG_MISS_REPO_DATA, Map.of());
        }

        log.info("conversationId={}, phase=GITHUB_SUMMARIZE, start render summary, repo={}/{}",
                conversationId, repoInfo.owner(), repoInfo.name());

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

        // 速读摘要输出后暂停，询问用户是否需要继续分析核心目录架构；
        // 用户回复「好」后由 GITHUB_DIRECTORY_ANALYZE 阶段执行目录分析，回复「不用」则结束。
        String askDirectory = reply + "\n\n👉 需要我继续分析它的核心目录架构吗？回复「好」查看，回复「不用」跳过。";
        log.info("conversationId={}, phase=GITHUB_SUMMARIZE, ask for directory analysis", conversationId);
        return PhaseExecutionResult.waitForUser(
                AgentPhase.GITHUB_DIRECTORY_ANALYZE,
                askDirectory,
                Map.of(
                        AgentArtifacts.REQUEST_REPOSITORY, repoInfo.owner() + "/" + repoInfo.name(),
                        AgentArtifacts.REPO_INFO, repoInfo
                )
        );
    }

    /**
     * 空值兜底工具方法
     * @param text 原始文本
     * @param fallback 为空时展示默认文案
     * @return 处理后文本
     */
    private String defaultText(String text, String fallback) {
        return text == null || text.isBlank() ? fallback : text.trim();
    }
}