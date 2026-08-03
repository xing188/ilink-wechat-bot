package com.ilink.test.ilinkcheck.application.agent.dto.github;

/**
 * GitHub 仓库目录树节点。
 * 对应 GitHub contents API 返回的单个条目。
 */
public record RepoTreeNode(
        String name,
        String path,
        String type,
        long size,
        String sha
) {

    public boolean isDirectory() {
        return "dir".equalsIgnoreCase(type);
    }

    public boolean isFile() {
        return "file".equalsIgnoreCase(type);
    }
}
