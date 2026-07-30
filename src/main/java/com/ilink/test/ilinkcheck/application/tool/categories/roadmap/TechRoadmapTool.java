package com.ilink.test.ilinkcheck.application.tool.categories.roadmap;

import com.ilink.test.ilinkcheck.application.skill.TechRoadmapSkill;
import com.ilink.test.ilinkcheck.application.tool.ChatFunctionTool;
import com.ilink.test.ilinkcheck.application.tool.ToolMetadata;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 技术路线规划工具（人4 核心交付之一）。
 *
 * <p>用 Spring AI 的 {@code @Tool} 把 {@link TechRoadmapSkill} 暴露给大模型，
 * 让 AI 在普通对话或工具编排模式下也能主动调用“生成学习路线”能力。</p>
 *
 * <p>注意必须重写 {@link #metadata()}，否则 {@code ToolRegistry} 会用默认占位信息，
 * 导致工具 id / 选择关键词缺失。</p>
 */
@Component
public class TechRoadmapTool implements ChatFunctionTool {

    private final TechRoadmapSkill techRoadmapSkill;

    public TechRoadmapTool(TechRoadmapSkill techRoadmapSkill) {
        this.techRoadmapSkill = techRoadmapSkill;
    }

    @Tool(
            name = "generateTechRoadmap",
            description = """
                    根据用户给出的技术主题，生成一份结构化的学习路线，并推荐相关的海外技术博文（Dev.to）与补充参考资料。
                    当用户说“我想学 XX”“XX 学习路线”“帮我规划 XX 的入门路线”“XX 怎么学”时使用。
                    """
    )
    public String generateTechRoadmap(
            @ToolParam(required = true, description = "要规划学习路线的技术主题，例如：eBPF、RAG、Spring AI、微服务")
            String topic
    ) {
        return techRoadmapSkill.generateRoadmap(topic);
    }

    @Override
    public ToolMetadata metadata() {
        return new ToolMetadata(
                "techRoadmap",
                "根据技术主题生成结构化学习路线，并推荐 Dev.to 博文与参考资料。",
                List.of("学习路线", "技术路线", "想学", "入门", "roadmap", "规划学习"),
                List.of("帮我规划 eBPF 的学习路线", "我想学 RAG，怎么入门", "Spring AI 的入门路线"),
                "当用户明确想为某个技术主题规划学习路线或寻找入门资料时，使用此工具。"
        );
    }
}
