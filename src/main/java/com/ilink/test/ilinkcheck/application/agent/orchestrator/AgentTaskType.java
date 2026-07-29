package com.ilink.test.ilinkcheck.application.agent.orchestrator;

/**
 * Agent 顶层任务类型。
 * 这一层描述“整条工作流要解决什么问题”，而不是单个工具怎么调用。
 */
public enum AgentTaskType {

    /**
     * 热点抓取 -> 舆情分析 -> 选题生成 -> 用户选择 -> 草稿生成。
     */
    HOTSPOT_ANALYSIS
}
