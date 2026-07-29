package com.ilink.test.ilinkcheck.application.agent.orchestrator.model;

/**
 * Agent 顶层任务类型。
 * 这一层只描述“整条工作流想解决什么问题”，不关心具体怎么调工具。
 */
public enum AgentTaskType {

    /**
     * GitHub 仓库速读。
     * 典型输入：仓库链接、owner/repo、"帮我看看这个库是做什么的"。
     */
    GITHUB_REPO_DIGEST,

    /**
     * 技术学习路线规划。
     * 典型输入："我想学 eBPF，帮我规划学习路线"。
     */
    TECH_ROADMAP,

    /**
     * 技术文档或博客草稿审稿。
     * 典型输入："帮我审一下这篇博客草稿"。
     */
    DOC_REVIEW,

    /**
     * 每日技术晨报订阅或生成。
     * 典型输入："每天 08:30 给我推送技术晨报"。
     */
    DAILY_DIGEST_SUBSCRIBE
}
