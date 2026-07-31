package com.ilink.test.ilinkcheck.application.skill;

import com.ilink.test.ilinkcheck.application.agent.dto.roadmap.DevToArticle;
import com.ilink.test.ilinkcheck.application.agent.dto.roadmap.SearchResult;
import com.ilink.test.ilinkcheck.application.agent.feature.roadmap.RoadmapComposeService;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.support.AgentTextUtils;
import com.ilink.test.ilinkcheck.application.agent.spi.mcp.DevToArticleProvider;
import com.ilink.test.ilinkcheck.application.agent.spi.mcp.WebSearchProvider;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 技术路线规划 Skill（人4 核心交付之一）。
 *
 * <p>这是一个“自包含”的业务能力封装：内部完成“提取主题 → 取 Dev.to 文章 → 取 WebSearch 补充
 * → 编排成学习路线”的完整闭环，不依赖外部阶段执行器。因此既能被 {@code SkillManager}
 * 自动收集复用，也能被 {@code TechRoadmapTool} 直接调用。</p>
 *
 * <p>取数只依赖 SPI 接口 {@link DevToArticleProvider} / {@link WebSearchProvider}，
 * 不写任何 HTTP 调用；真实实现由人2 通过 {@code @Bean} 提供（当前由 {@code AgentMockConfig} 兜底）。</p>
 */
@Component
public class TechRoadmapSkill implements Skill {

    private static final int ARTICLE_LIMIT = 3;
    private static final int REFERENCE_LIMIT = 2;

    private final DevToArticleProvider devToArticleProvider;
    private final WebSearchProvider webSearchProvider;
    private final RoadmapComposeService composeService;

    public TechRoadmapSkill(DevToArticleProvider devToArticleProvider,
                            WebSearchProvider webSearchProvider,
                            RoadmapComposeService composeService) {
        this.devToArticleProvider = devToArticleProvider;
        this.webSearchProvider = webSearchProvider;
        this.composeService = composeService;
    }

    @Override
    public String id() {
        return "tech-roadmap";
    }

    @Override
    public String name() {
        return "技术路线规划";
    }

    @Override
    public String description() {
        return "根据一个技术主题，检索 Dev.to 文章与网络资料，生成结构化的学习路线与海外博文推荐。";
    }

    /**
     * Skill 统一入口：入参是一整句用户消息，内部自行提取主题。
     */
    @Override
    public String execute(String input) {
        String topic = AgentTextUtils.extractTopic(input).orElse(null);
        if (topic == null) {
            return "你想学什么技术？直接告诉我一个主题，比如 `eBPF`、`RAG` 或 `Spring AI`，"
                    + "我帮你规划学习路线并推荐优质博文。";
        }
        return generateRoadmap(topic);
    }

    /**
     * 给定明确主题，直接生成学习路线。供 Tool 或其它调用方使用。
     */
    public String generateRoadmap(String topic) {
        List<DevToArticle> articles = devToArticleProvider.fetchByTag(topic, ARTICLE_LIMIT);
        List<SearchResult> references = webSearchProvider.search(topic + " tutorial", REFERENCE_LIMIT);
        return composeService.buildReply(topic, articles, references);
    }
}
