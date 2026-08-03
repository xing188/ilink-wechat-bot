package com.ilink.test.ilinkcheck.application.agent.feature.github;

import com.ilink.test.ilinkcheck.application.agent.dto.github.RepoInfo;
import com.ilink.test.ilinkcheck.application.agent.dto.github.RepoTreeNode;
import com.ilink.test.ilinkcheck.application.agent.dto.github.TrendingRepo;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.model.AgentArtifacts;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.model.AgentState;
import com.ilink.test.ilinkcheck.application.agent.spi.mcp.GitHubRepoProvider;
import com.ilink.test.ilinkcheck.infrastructure.common.TimeoutExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.stream.Collectors;

/**
 * GitHub 仓库速读业务逻辑。
 * 负责从用户输入解析仓库信息、调用 Provider 获取数据、组装摘要回复。
 */
@Slf4j
@Service
public class GitHubRepoDigestService {

    /** 目录架构分析时最多展开的一级目录个数（避免对超大仓库产生过多 API 调用）。 */
    private static final int MAX_SCAN_DIRS = 8;
    /** 每个一级目录下最多展示的二级子目录个数。 */
    private static final int MAX_SUB_DIRS = 6;

    /** 目录树展开的最大深度（选中目录起向下）。 */
    private static final int TREE_MAX_DEPTH = 3;
    /** 目录树渲染的总节点数上限，防止超大目录把回复撑爆。 */
    private static final int TREE_MAX_NODES = 40;

    /** 代码讲解时最多取样的代码文件数。 */
    private static final int CODE_SAMPLE_FILES = 3;
    /** 单个代码文件取样片段的最大字符数。 */
    private static final int CODE_SNIPPET_CHARS = 1500;

    /** 视为代码/配置文件的扩展名（讲解时优先取样这些文件）。 */
    private static final Set<String> CODE_EXTENSIONS = Set.of(
            "java", "kt", "py", "go", "rs", "js", "ts", "jsx", "tsx",
            "c", "cc", "cpp", "h", "hpp", "cs", "rb", "php", "sql",
            "sh", "bash", "yml", "yaml", "xml", "json", "properties",
            "gradle", "toml", "cfg", "ini", "dockerfile"
    );

    /** 代码讲解最长等待时间（毫秒），超时降级为结构说明。 */
    private long codeExplainTimeoutMs = 20000;

    /** 代码讲解系统提示词：要求输出简洁、贴合样本、不编造。 */
    private static final String CODE_EXPLAIN_SYSTEM_PROMPT = """
            你是一名资深软件工程师。请基于提供的 GitHub 目录结构与代码片段，用中文简要讲解该目录的职责、关键文件作用与实现思路。
            要求：
            - 控制在 10 行以内，重点突出；
            - 结合给出的代码片段讲，不要泛泛而谈；
            - 信息不足时明确说明，不要编造。
            """;

    /** 代码讲解用 ChatClient（可选）。来自 {@code plannerChatClient}；未注入时走结构说明降级。 */
    private ChatClient plannerClient;

    @Autowired(required = false)
    public void setPlannerClient(@Qualifier("plannerChatClient") ChatClient plannerClient) {
        this.plannerClient = plannerClient;
    }

    @Value("${app.agent.code-explain-timeout-ms:20000}")
    public void setCodeExplainTimeoutMs(long codeExplainTimeoutMs) {
        this.codeExplainTimeoutMs = codeExplainTimeoutMs;
    }

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
        sj.add("  • 回复「看代码」→ 提炼核心目录架构");
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

    /**
     * 获取仓库根目录树（用于"看代码"场景）
     */
    public List<RepoTreeNode> fetchRootDirectory(String owner, String repo) {
        return gitHubRepoProvider.fetchDirectoryTree(owner, repo, "");
    }

    /**
     * 组装核心目录架构分析。
     * 从根目录树中识别出核心模块目录，并按类型分组展示；
     * 每个一级目录会进一步展开其二级子目录，让结构更明确。
     *
     * @param owner 仓库所有者
     * @param repo  仓库名
     * @param nodes 根目录条目
     * @return 目录架构分析文本
     */
    public String buildDirectoryArchitecture(String owner, String repo, List<RepoTreeNode> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return "暂时无法获取该仓库的目录结构。";
        }

        StringJoiner sj = new StringJoiner("\n");
        sj.add("📂 " + owner + "/" + repo + " 核心目录架构");
        sj.add("");

        // 目录（模块）
        List<RepoTreeNode> dirs = nodes.stream()
                .filter(RepoTreeNode::isDirectory)
                .toList();

        // 文件（根级关键文件）
        List<RepoTreeNode> files = nodes.stream()
                .filter(RepoTreeNode::isFile)
                .filter(n -> isKeyRootFile(n.name()))
                .toList();

        if (!dirs.isEmpty()) {
            sj.add("📁 核心模块目录：");
            // 最多展开前 N 个一级目录的二级结构，避免对超大仓库产生过多 API 调用
            List<RepoTreeNode> scanned = dirs.size() > MAX_SCAN_DIRS
                    ? dirs.subList(0, MAX_SCAN_DIRS)
                    : dirs;
            for (RepoTreeNode dir : scanned) {
                sj.add("  ├─ " + dir.name() + "/");
                List<RepoTreeNode> subDirs = fetchSubDirectories(owner, repo, dir.name());
                for (RepoTreeNode sub : subDirs) {
                    sj.add("  │    └─ " + sub.name() + "/");
                }
            }
            if (dirs.size() > scanned.size()) {
                sj.add("  └─ … 等 " + (dirs.size() - scanned.size()) + " 个目录（回复目录名可继续展开）");
            }
            sj.add("");
        }

        if (!files.isEmpty()) {
            sj.add("📄 根级关键文件：");
            for (RepoTreeNode file : files) {
                sj.add("  ├─ " + file.name());
            }
            sj.add("");
        }

        sj.add("💡 核心模块说明：");
        List<String> interpretations = interpretDirectories(dirs);
        if (interpretations.isEmpty()) {
            sj.add("  目录结构较扁平，可查看子目录或关键文件深入了解。");
        } else {
            for (String line : interpretations) {
                sj.add("  " + line);
            }
        }

        sj.add("");
        sj.add("👆 想深入某个模块，直接告诉我目录名，我可以继续展开分析。");
        return sj.toString();
    }

    /**
     * 获取某个一级目录下的二级子目录（按名称排序，最多 {@link #MAX_SUB_DIRS} 个）。
     * 单目录获取失败不影响整体分析。
     */
    private List<RepoTreeNode> fetchSubDirectories(String owner, String repo, String dirName) {
        try {
            return gitHubRepoProvider.fetchDirectoryTree(owner, repo, dirName).stream()
                    .filter(RepoTreeNode::isDirectory)
                    .sorted(Comparator.comparing(RepoTreeNode::name))
                    .limit(MAX_SUB_DIRS)
                    .toList();
        } catch (Exception e) {
            log.warn("获取子目录失败: owner={}, repo={}, dir={}, reason={}",
                    owner, repo, dirName, e.getMessage());
            return List.of();
        }
    }

    /**
     * 获取指定目录的条目列表（用于"继续展开"场景）。
     */
    public List<RepoTreeNode> fetchDirectory(String owner, String repo, String path) {
        return gitHubRepoProvider.fetchDirectoryTree(owner, repo, path == null ? "" : path);
    }

    /**
     * 构建「目录名 → 完整路径」索引，用于把用户回复的短目录名解析成真实路径。
     * 覆盖根目录及其二级子目录；随展开动态补充。
     */
    public Map<String, String> buildPathIndex(String owner, String repo, List<RepoTreeNode> rootNodes) {
        Map<String, String> index = new HashMap<>();
        if (rootNodes == null) {
            return index;
        }
        List<RepoTreeNode> dirs = rootNodes.stream()
                .filter(RepoTreeNode::isDirectory)
                .limit(MAX_SCAN_DIRS)
                .toList();
        for (RepoTreeNode dir : dirs) {
            index.put(dir.name(), dir.path());
            for (RepoTreeNode sub : fetchSubDirectories(owner, repo, dir.name())) {
                index.put(sub.name(), sub.path());
            }
        }
        return index;
    }

    /**
     * 组装某个目录展开后的内容（子目录 + 文件）。
     */
    public String buildDirectoryExpand(String dirPath, List<RepoTreeNode> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return "该目录下没有更多内容，或它不是有效的目录。";
        }

        List<RepoTreeNode> dirs = nodes.stream()
                .filter(RepoTreeNode::isDirectory)
                .sorted(Comparator.comparing(RepoTreeNode::name))
                .toList();
        List<RepoTreeNode> files = nodes.stream()
                .filter(RepoTreeNode::isFile)
                .sorted(Comparator.comparing(RepoTreeNode::name))
                .toList();

        StringJoiner sj = new StringJoiner("\n");
        sj.add("📂 目录 `" + (dirPath == null || dirPath.isBlank() ? "/" : dirPath) + "/` 内容：");
        sj.add("");
        if (!dirs.isEmpty()) {
            sj.add("📁 子目录：");
            for (RepoTreeNode dir : dirs) {
                sj.add("  ├─ " + dir.name() + "/");
            }
            sj.add("");
        }
        if (!files.isEmpty()) {
            sj.add("📄 文件：");
            for (RepoTreeNode file : files) {
                sj.add("  ├─ " + file.name());
            }
            sj.add("");
        }
        if (dirs.isEmpty() && files.isEmpty()) {
            sj.add("  （空目录）");
            sj.add("");
        }
        sj.add("👆 想继续深入，直接回复子目录名；回复「退出」结束本次目录浏览。");
        return sj.toString();
    }

    /**
     * 构建选中目录的完整子目录树（递归展开至 {@link #TREE_MAX_DEPTH}），
     * 并同步生成「目录名→完整路径」索引，供后续按名字定位代码目录。
     */
    public DirectoryTreeView buildDeepDirectoryTree(String owner, String repo, String rootPath, int maxDepth) {
        Map<String, String> pathIndex = new HashMap<>();
        StringBuilder sb = new StringBuilder();
        String label = (rootPath == null || rootPath.isBlank()) ? "/" : rootPath;
        sb.append("📂 目录 `").append(label).append("/` 结构：").append('\n');

        List<RepoTreeNode> root = fetchDirectory(owner, repo, rootPath);
        int[] counter = {0};
        appendTreeNodes(sb, pathIndex, owner, repo, root, "", maxDepth, 0, counter);

        if (counter[0] == 0) {
            return new DirectoryTreeView("该目录下没有更多内容，或它不是有效的目录。", pathIndex);
        }
        if (counter[0] >= TREE_MAX_NODES) {
            sb.append('\n').append("… 目录较大，仅展示部分，可回复具体目录名继续查看。");
        }
        return new DirectoryTreeView(sb.toString().strip(), pathIndex);
    }

    private void appendTreeNodes(StringBuilder sb,
                                 Map<String, String> pathIndex,
                                 String owner,
                                 String repo,
                                 List<RepoTreeNode> nodes,
                                 String indent,
                                 int maxDepth,
                                 int depth,
                                 int[] counter) {
        if (nodes == null || nodes.isEmpty() || depth > maxDepth || counter[0] >= TREE_MAX_NODES) {
            return;
        }
        List<RepoTreeNode> dirs = nodes.stream()
                .filter(RepoTreeNode::isDirectory)
                .sorted(Comparator.comparing(RepoTreeNode::name))
                .toList();
        List<RepoTreeNode> files = nodes.stream()
                .filter(RepoTreeNode::isFile)
                .sorted(Comparator.comparing(RepoTreeNode::name))
                .limit(4)
                .toList();

        for (RepoTreeNode dir : dirs) {
            if (counter[0] >= TREE_MAX_NODES) {
                return;
            }
            sb.append(indent).append("├─ ").append(dir.name()).append('/').append('\n');
            counter[0]++;
            pathIndex.put(dir.name(), dir.path());
            appendTreeNodes(sb, pathIndex, owner, repo,
                    fetchDirectory(owner, repo, dir.path()), indent + "│   ", maxDepth, depth + 1, counter);
        }
        for (RepoTreeNode file : files) {
            if (counter[0] >= TREE_MAX_NODES) {
                return;
            }
            sb.append(indent).append("├─ ").append(file.name()).append('\n');
            counter[0]++;
        }
    }

    /**
     * 讲解某个目录的代码。
     * 优先：取样少量代码文件内容交给 LLM 生成中文讲解；
     * 降级：基于文件/子目录命名给出结构化说明。
     */
    public String explainDirectoryCode(String owner, String repo, String dirPath) {
        List<RepoTreeNode> nodes = fetchDirectory(owner, repo, dirPath);
        if (nodes == null || nodes.isEmpty()) {
            return "目录 `" + dirPath + "` 中没有可讲解的内容，或它不是有效的目录。回复「退出」结束浏览。";
        }

        List<RepoTreeNode> dirs = nodes.stream()
                .filter(RepoTreeNode::isDirectory)
                .sorted(Comparator.comparing(RepoTreeNode::name))
                .toList();
        List<RepoTreeNode> files = nodes.stream()
                .filter(RepoTreeNode::isFile)
                .sorted(Comparator.comparing(RepoTreeNode::name))
                .toList();

        if (plannerClient != null) {
            try {
                List<String> snippets = files.stream()
                        .filter(this::isCodeFile)
                        .limit(CODE_SAMPLE_FILES)
                        .map(file -> fetchFileSnippet(owner, repo, file))
                        .filter(StringUtils::hasText)
                        .toList();
                String prompt = buildCodeExplainPrompt(dirPath, dirs, files, snippets);
                String result = TimeoutExecutor.executeOrThrow(
                        () -> plannerClient.prompt()
                                .system(CODE_EXPLAIN_SYSTEM_PROMPT)
                                .user(prompt)
                                .call()
                                .content(),
                        codeExplainTimeoutMs,
                        "目录代码讲解");
                if (StringUtils.hasText(result)) {
                    return "🧭 目录 `" + dirPath + "` 代码讲解：\n\n" + result.trim();
                }
            } catch (Exception e) {
                log.warn("LLM 代码讲解失败，降级为结构说明: dir={}, reason={}", dirPath, e.getMessage());
            }
        }

        return buildStructuralExplanation(dirPath, dirs, files);
    }

    private String fetchFileSnippet(String owner, String repo, RepoTreeNode file) {
        try {
            String content = gitHubRepoProvider.fetchFileContent(owner, repo, file.path());
            if (!StringUtils.hasText(content)) {
                return "";
            }
            String trimmed = content.trim();
            if (trimmed.length() > CODE_SNIPPET_CHARS) {
                trimmed = trimmed.substring(0, CODE_SNIPPET_CHARS) + "\n…（截断）";
            }
            return "### " + file.path() + "\n" + trimmed;
        } catch (Exception e) {
            log.warn("获取代码文件内容失败: path={}, reason={}", file.path(), e.getMessage());
            return "";
        }
    }

    private String buildCodeExplainPrompt(String dirPath,
                                          List<RepoTreeNode> dirs,
                                          List<RepoTreeNode> files,
                                          List<String> snippets) {
        StringJoiner sj = new StringJoiner("\n");
        sj.add("请讲解这个 GitHub 目录的代码：").add("");
        sj.add("目录：" + dirPath);
        if (!dirs.isEmpty()) {
            sj.add("子目录：" + dirs.stream().map(RepoTreeNode::name).collect(Collectors.joining(", ")));
        }
        if (!files.isEmpty()) {
            sj.add("文件：" + files.stream().map(RepoTreeNode::name).collect(Collectors.joining(", ")));
        }
        sj.add("");
        if (snippets.isEmpty()) {
            sj.add("（没有取样到代码片段，请基于目录与文件命名给出合理推断，不要编造具体实现。）");
        } else {
            sj.add("关键代码片段：").add(String.join("\n\n", snippets));
        }
        return sj.toString();
    }

    private String buildStructuralExplanation(String dirPath,
                                              List<RepoTreeNode> dirs,
                                              List<RepoTreeNode> files) {
        StringJoiner sj = new StringJoiner("\n");
        sj.add("📚 目录 `" + dirPath + "/` 结构说明：");
        sj.add("");
        if (!dirs.isEmpty()) {
            sj.add("📁 子目录：");
            for (RepoTreeNode dir : dirs) {
                sj.add("  ├─ " + dir.name() + "/");
            }
            sj.add("");
        }
        if (!files.isEmpty()) {
            sj.add("📄 文件：");
            for (RepoTreeNode file : files) {
                sj.add("  ├─ " + file.name());
            }
            sj.add("");
        }
        sj.add("💡 该目录包含 " + dirs.size() + " 个子目录、" + files.size() + " 个文件。");
        sj.add("想深入讲解某个子目录，直接回复目录名；回复「退出」结束浏览。");
        return sj.toString();
    }

    private boolean isCodeFile(RepoTreeNode file) {
        String name = file.name().toLowerCase();
        if (name.startsWith("readme") || name.endsWith(".md") || name.endsWith(".txt") || name.endsWith(".license")) {
            return false;
        }
        int dot = name.lastIndexOf('.');
        if (dot < 0) {
            return false;
        }
        return CODE_EXTENSIONS.contains(name.substring(dot + 1));
    }

    /**
     * 根据常见的开源项目目录约定，推断每个目录的职责。
     */
    private List<String> interpretDirectories(List<RepoTreeNode> dirs) {
        List<String> lines = new ArrayList<>();
        for (RepoTreeNode dir : dirs) {
            String name = dir.name().toLowerCase();
            String desc = switch (name) {
                case "src", "source", "lib", "packages" -> "核心源码（业务逻辑实现）";
                case "core", "kernel", "engine" -> "核心引擎/框架（项目主逻辑）";
                case "app", "applications", "apps" -> "应用入口（可运行程序）";
                case "api", "apis", "server", "service", "services" -> "对外服务/API 接口层";
                case "web", "ui", "frontend", "client" -> "前端/客户端界面";
                case "cli", "cmd", "bin" -> "命令行工具/可执行入口";
                case "models", "model", "entities", "entity" -> "数据模型/实体定义";
                case "data", "db", "database", "migrations" -> "数据访问/数据库脚本";
                case "docs", "documentation", "wiki" -> "项目文档";
                case "tests", "test", "spec", "e2e" -> "测试用例";
                case "scripts", "tools", "util", "utils" -> "辅助脚本/工具函数";
                case "config", "conf", "configs" -> "配置文件";
                case "docker", "deploy", "k8s", "kubernetes" -> "部署/容器化配置";
                case "examples", "demo", "sample", "samples" -> "示例代码";
                case "resources", "assets", "public", "static" -> "静态资源";
                default -> "";
            };
            if (!desc.isEmpty()) {
                lines.add("• `" + dir.name() + "/` → " + desc);
            }
        }
        return lines;
    }

    /**
     * 判断是否为根级关键文件（构建配置、说明文档、许可证等）。
     */
    private boolean isKeyRootFile(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return false;
        }
        String lower = fileName.toLowerCase();
        return lower.equals("readme.md") || lower.equals("readme")
                || lower.equals("pom.xml") || lower.equals("build.gradle")
                || lower.equals("build.gradle.kts") || lower.equals("package.json")
                || lower.equals("go.mod") || lower.equals("cargo.toml")
                || lower.equals("requirements.txt") || lower.equals("pyproject.toml")
                || lower.equals("dockerfile") || lower.equals("docker-compose.yml")
                || lower.equals("docker-compose.yaml") || lower.equals("license")
                || lower.equals("makefile") || lower.equals("cmakelists.txt");
    }

    private String defaultText(String text, String fallback) {
        return StringUtils.hasText(text) ? text.trim() : fallback;
    }

    /**
     * 目录树渲染结果：文本 + 目录名→完整路径索引。
     */
    public record DirectoryTreeView(String text, Map<String, String> pathIndex) {
    }
}
