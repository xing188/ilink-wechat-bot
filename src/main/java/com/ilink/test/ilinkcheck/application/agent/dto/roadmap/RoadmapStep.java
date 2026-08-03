package com.ilink.test.ilinkcheck.application.agent.dto.roadmap;

/**
 * 学习路线中的单个步骤。
 *
 * 每个步骤包含标题和详细说明。详细说明字段预留用于后续 LLM CoT 编排
 * 或人工补充具体知识点（例如"语法"步骤下写明"变量/循环/指针"等）。
 */
public record RoadmapStep(
        String title,
        String description
) {
    /**
     * 便捷工厂：只有标题，说明留空（向后兼容旧调用方）。
     */
    public static RoadmapStep ofTitle(String title) {
        return new RoadmapStep(title, "");
    }

    /**
     * 便捷工厂：标题 + 说明。
     */
    public static RoadmapStep of(String title, String description) {
        return new RoadmapStep(title, description != null ? description : "");
    }
}
