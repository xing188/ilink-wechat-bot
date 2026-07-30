package com.ilink.test.ilinkcheck.application.tool.categories.github;

import com.ilink.test.ilinkcheck.application.agent.feature.github.GitHubRepoDigestService;
import com.ilink.test.ilinkcheck.application.tool.ChatFunctionTool;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * GitHub 仓库速读工具。
 * 通过 Spring AI @Tool 注解暴露给模型，当用户询问仓库分析、项目速读时自动调用。
 */
@Component
public class GitHubRepoDigestTool implements ChatFunctionTool {

    private final GitHubRepoDigestService gitHubRepoDigestService;

    public GitHubRepoDigestTool(GitHubRepoDigestService gitHubRepoDigestService) {
        this.gitHubRepoDigestService = gitHubRepoDigestService;
    }

    @Tool(
            name = "queryGitHubRepoDigest",
            description = """
                    分析 GitHub 仓库，获取仓库的描述、Star 数、Fork 数、技术栈、README 摘要等信息。
                    当用户询问仓库是做什么的、分析某个开源项目、查看 repo 信息、速读仓库时使用。
                    支持 GitHub 链接（https://github.com/owner/repo）或 owner/repo 格式。
                    返回结果已是格式化后的完整回复，请直接发送给用户。
                    """
    )
    public String queryRepoDigest(
            @ToolParam(required = true, description = "GitHub 仓库标识，支持 owner/repo 格式或完整 GitHub 链接，例如 lodash/lodash 或 https://github.com/lodash/lodash")
            String repository) {

        String repoFull = extractRepoFull(repository);
        if (repoFull == null) {
            return "请提供 GitHub 仓库链接或 owner/repo 格式的仓库名，例如：lodash/lodash。";
        }

        var repoInfo = gitHubRepoDigestService.fetchRepoInfo(createMinimalState(repoFull));
        if (repoInfo == null) {
            return "没有找到该仓库的信息，请检查仓库名是否正确。";
        }
        return gitHubRepoDigestService.buildDigest(repoInfo);
    }

    @Tool(
            name = "queryGitHubTrending",
            description = """
                    查询 GitHub 趋势仓库。支持按语言和时间范围筛选。
                    当用户问"今天有什么热门项目""GitHub 趋势""最近流行的项目"时使用。
                    """
    )
    public String queryTrending(
            @ToolParam(required = false, description = "编程语言筛选，如 java、python、javascript，不传则查询全部")
            String language,
            @ToolParam(required = false, description = "时间范围：daily/weekly/monthly，默认 daily")
            String since) {

        String lang = language != null && !language.isBlank() ? language.trim() : "";
        String timeRange = since != null && !since.isBlank() ? since.trim() : "daily";

        var repos = gitHubRepoDigestService.fetchTrending(lang, timeRange);
        return gitHubRepoDigestService.buildTrendingDigest(repos, lang, timeRange);
    }

    private String extractRepoFull(String input) {
        if (input == null || input.isBlank()) return null;
        String trimmed = input.trim();
        // owner/repo 格式
        if (trimmed.matches("^[a-zA-Z0-9._-]+/[a-zA-Z0-9._-]+$")) {
            return trimmed;
        }
        // GitHub URL 格式
        var m = java.util.regex.Pattern.compile("github\\.com/([^/\\s]+)/([^/\\s]+)")
                .matcher(trimmed);
        return m.find() ? m.group(1) + "/" + m.group(2) : null;
    }

    private com.ilink.test.ilinkcheck.application.agent.orchestrator.model.AgentState createMinimalState(String repoFull) {
        return com.ilink.test.ilinkcheck.application.agent.orchestrator.model.AgentState.newWorkflow(
                "tool:" + hashCode(),
                com.ilink.test.ilinkcheck.application.agent.orchestrator.model.AgentTaskType.GITHUB_REPO_DIGEST,
                com.ilink.test.ilinkcheck.application.agent.orchestrator.model.AgentPhase.GITHUB_FETCH,
                "分析仓库：" + repoFull
        );
    }
}
