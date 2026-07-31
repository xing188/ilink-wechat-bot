package com.ilink.test.ilinkcheck.application.agent.feature.github;

import com.ilink.test.ilinkcheck.application.agent.dto.github.RepoInfo;
import com.ilink.test.ilinkcheck.application.agent.dto.github.TrendingRepo;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.model.AgentArtifacts;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.model.AgentState;
import com.ilink.test.ilinkcheck.application.agent.spi.mcp.GitHubRepoProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.StringJoiner;

/**
 * GitHub 仓库速读业务逻辑。
 * 负责从用户输入解析仓库信息、调用 Provider 获取数据、组装摘要回复。
 */
@Service
public class GitHubRepoDigestService {

    private final GitHubRepoProvider gitHubRepoProvider;

    public GitHubRepoDigestService(GitHubRepoProvider gitHubRepoProvider) {
        this.gitHubRepoProvider = gitHubRepoProvider;
    }

    /**
     * 从 AgentState 提取仓库信息并获取仓库数据
     */
    public RepoInfo fetchRepoInfo(AgentState state) {
        String repoFull = state.getArtifact(AgentArtifacts.REQUEST_REPOSITORY, String.class);
        if (repoFull == null || !repoFull.contains("/")) {
            return null;
        }
        String[] parts = repoFull.split("/", 2);
        return gitHubRepoProvider.fetchRepo(parts[0], parts[1]);
    }

    /**
     * 获取趋势仓库
     */
    public List<TrendingRepo> fetchTrending(String language, String since) {
        return gitHubRepoProvider.fetchTrending(language, since);
    }

    /**
     * 组装速读摘要
     * 遵循「开源项目源码/架构解析类」结构模板：
     *   一句话解决什么问题 → 整体架构/模块划分 → 核心数据流/执行链路 → 关键代码块解读 → 优缺点分析
     */
    public String buildDigest(RepoInfo repoInfo) {
        StringJoiner sj = new StringJoiner("\n");
        sj.add("📦 GitHub 仓库速读：" + repoInfo.owner() + "/" + repoInfo.name());
        sj.add("");

        // 1. 一句话解决什么问题
        sj.add("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        sj.add("🎯 一句话定位");
        sj.add("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        sj.add(defaultText(repoInfo.description(), "暂无描述"));
        sj.add("");

        // 元信息
        sj.add("🔤 语言：" + defaultText(repoInfo.language(), "未标注") +
                "  ⭐ " + repoInfo.stars() + "  ⑂ " + repoInfo.forks());
        if (StringUtils.hasText(repoInfo.techStack())) {
            sj.add("🛠 技术栈：" + repoInfo.techStack());
        }
        sj.add("");

        // 2. 整体架构/模块划分（从 README 摘要中提取）
        String summary = defaultText(repoInfo.readmeSummary(), "");
        if (!summary.isEmpty()) {
            sj.add("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            sj.add("📐 整体架构与模块划分");
            sj.add("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            // 尝试从 README 摘要中提取架构信息
            String architecture = extractSection(summary, new String[]{
                    "架构", "整体设计", "模块", "Architecture", "Structure", "Design"});
            if (architecture != null) {
                sj.add(architecture);
            } else {
                // 没有明确的架构描述，输出 README 前段作为参考
                sj.add(summary.length() > 300 ? summary.substring(0, 300) + "…" : summary);
            }
            sj.add("");
        }

        // 3. 核心数据流/执行链路
        if (!summary.isEmpty()) {
            String flow = extractSection(summary, new String[]{
                    "数据流", "执行流程", "工作流", "链路", "Flow", "Workflow", "Pipeline"});
            if (flow != null) {
                sj.add("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                sj.add("⚡ 核心数据流 / 执行链路");
                sj.add("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                sj.add(flow);
                sj.add("");
            }
        }

        // 4. 关键代码块解读
        if (!summary.isEmpty()) {
            String codeInsight = extractSection(summary, new String[]{
                    "关键代码", "核心实现", "示例", "Example", "Usage", "Quick Start"});
            if (codeInsight != null) {
                sj.add("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                sj.add("🔑 关键代码 / 快速上手");
                sj.add("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                sj.add(codeInsight);
                sj.add("");
            }
        }

        // 5. 优缺点分析（基于元信息推断）
        sj.add("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        sj.add("📊 项目概况");
        sj.add("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        sj.add("⭐ Star：" + repoInfo.stars() + "  ⑂ Fork：" + repoInfo.forks());
        if (StringUtils.hasText(repoInfo.language())) {
            sj.add("🔤 主要语言：" + repoInfo.language());
        }
        if (StringUtils.hasText(repoInfo.techStack())) {
            sj.add("🛠 技术栈：" + repoInfo.techStack());
        }
        sj.add("");

        sj.add("💡 你可以继续深入：");
        sj.add("  • 这个项目的整体架构是怎样的？");
        sj.add("  • 怎么快速上手？");
        sj.add("  • 帮我分析它的代码质量");
        return sj.toString();
    }

    /**
     * 从 README 摘要中按关键词提取段落
     */
    private String extractSection(String text, String[] keywords) {
        if (!StringUtils.hasText(text)) return null;
        for (String keyword : keywords) {
            int idx = text.indexOf(keyword);
            if (idx >= 0) {
                int start = Math.max(0, idx - 20);
                int end = Math.min(text.length(), idx + 200);
                // 向前找句号或换行作为段落起点
                int paraStart = text.lastIndexOf('\n', idx);
                if (paraStart >= start) start = paraStart;
                // 向后找句号或换行作为段落终点
                int paraEnd = text.indexOf('\n', idx + keyword.length());
                if (paraEnd > 0 && paraEnd < end) end = paraEnd;
                return text.substring(start, end).trim();
            }
        }
        return null;
    }

    /**
     * 组装趋势摘要
     */
    public String buildTrendingDigest(List<TrendingRepo> repos, String language, String since) {
        if (repos == null || repos.isEmpty()) {
            return "暂无趋势数据。";
        }

        StringJoiner sj = new StringJoiner("\n");
        sj.add("🔥 " + defaultText(language, "全部语言") + " 趋势 (" + defaultText(since, "今日") + ")");
        sj.add("");
        for (int i = 0; i < Math.min(repos.size(), 10); i++) {
            TrendingRepo r = repos.get(i);
            sj.add((i + 1) + ". " + r.owner() + "/" + r.name());
            if (StringUtils.hasText(r.description())) {
                sj.add("   " + r.description());
            }
            sj.add("   ⭐ " + r.formattedStarMetric() + (r.isFallbackMetric() ? "（官方回退）" : ""));
            sj.add("");
        }
        return sj.toString();
    }

    private String defaultText(String text, String fallback) {
        return StringUtils.hasText(text) ? text.trim() : fallback;
    }
}
