package com.ilink.test.ilinkcheck.application.agent.spi.mcp;

import com.ilink.test.ilinkcheck.application.agent.dto.github.RepoInfo;
import com.ilink.test.ilinkcheck.application.agent.dto.github.RepoTreeNode;
import com.ilink.test.ilinkcheck.application.agent.dto.github.TrendingRepo;

import java.util.List;

public interface GitHubRepoProvider {

    RepoInfo fetchRepo(String owner, String repo);

    List<TrendingRepo> fetchTrending(String language, String since);

    /**
     * 获取仓库目录树（根目录下的条目）。
     * 用于"看代码"场景下的核心目录架构分析。
     * <p>默认返回空列表，避免破坏未实现此方法的既有 Provider。</p>
     *
     * @param owner 仓库所有者
     * @param repo  仓库名
     * @param path  目录路径，传空字符串表示根目录
     * @return 目录条目列表（目录 + 文件）
     */
    default List<RepoTreeNode> fetchDirectoryTree(String owner, String repo, String path) {
        return java.util.Collections.emptyList();
    }

    /**
     * 获取单个文件的内容（用于目录代码讲解）。
     * <p>默认返回 null，避免破坏未实现此方法的既有 Provider。</p>
     *
     * @param owner 仓库所有者
     * @param repo  仓库名
     * @param path  文件完整路径，如 src/main/java/App.java
     * @return 文件文本内容；获取失败返回 null
     */
    default String fetchFileContent(String owner, String repo, String path) {
        return null;
    }
}
