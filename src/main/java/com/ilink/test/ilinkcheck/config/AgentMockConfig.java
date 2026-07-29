package com.ilink.test.ilinkcheck.config;

import com.ilink.test.ilinkcheck.application.agent.dto.HotSearchResult;
import com.ilink.test.ilinkcheck.application.agent.dto.KnowledgeItem;
import com.ilink.test.ilinkcheck.application.agent.dto.SentimentReport;
import com.ilink.test.ilinkcheck.application.agent.dto.WritingDraft;
import com.ilink.test.ilinkcheck.application.agent.spi.content.SentimentAnalyzer;
import com.ilink.test.ilinkcheck.application.agent.spi.content.WriterAgent;
import com.ilink.test.ilinkcheck.application.agent.spi.mcp.HotSearchProvider;
import com.ilink.test.ilinkcheck.application.agent.spi.rag.KnowledgeRetriever;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Agent 第一版默认假实现。
 * 只在项目里还没有真实 Provider / Agent Bean 时启用，方便先把主流程跑起来。
 */
@Configuration
public class AgentMockConfig {

    @Bean
    @ConditionalOnMissingBean(HotSearchProvider.class)
    public HotSearchProvider defaultHotSearchProvider() {
        return new HotSearchProvider() {
            @Override
            public List<HotSearchResult> fetchHotSearch(String source) {
                return buildMockResults(source == null || source.isBlank() ? "weibo" : source, "职场");
            }

            @Override
            public List<HotSearchResult> searchByKeyword(String keyword) {
                return buildMockResults("weibo", keyword == null || keyword.isBlank() ? "职场" : keyword.trim());
            }

            private List<HotSearchResult> buildMockResults(String source, String keyword) {
                return List.of(
                        new HotSearchResult(source, keyword + " 话题热度持续上升",
                                "讨论集中在机会与焦虑并存，普通人担心跟不上变化。",
                                "https://example.com/hotspot/1", 92, List.of(keyword, "机会", "焦虑")),
                        new HotSearchResult(source, keyword + " 从业门槛争议加剧",
                                "高频评论围绕信息差、执行力与平台规则展开。",
                                "https://example.com/hotspot/2", 88, List.of(keyword, "门槛", "规则")),
                        new HotSearchResult(source, keyword + " 内容创作者转型提速",
                                "不少讨论指向变现路径、选题效率和账号定位。",
                                "https://example.com/hotspot/3", 85, List.of(keyword, "变现", "定位"))
                );
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean(SentimentAnalyzer.class)
    public SentimentAnalyzer defaultSentimentAnalyzer() {
        return text -> new SentimentReport(
                "争议偏高",
                0.48,
                0.31,
                List.of("担心门槛越来越高", "不知道如何快速跟进趋势"),
                List.of("是否真的适合普通人入场", "红利期是否已经结束")
        );
    }

    @Bean
    @ConditionalOnMissingBean(KnowledgeRetriever.class)
    public KnowledgeRetriever defaultKnowledgeRetriever() {
        return new KnowledgeRetriever() {
            @Override
            public List<KnowledgeItem> search(String query) {
                return List.of(
                        new KnowledgeItem("knowledge-1", "冲突型开头模板",
                                "开头先抛出反常识判断，再接 1 个具体场景。",
                                "writing-template", "理性拆解", 0.93f),
                        new KnowledgeItem("knowledge-2", "情绪型收束模板",
                                "结尾用一句共鸣总结 + 一个行动建议，提高收藏率。",
                                "writing-template", "情绪共鸣", 0.89f)
                );
            }

            @Override
            public List<KnowledgeItem> searchByCategory(String category) {
                return search(category);
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean(WriterAgent.class)
    public WriterAgent defaultWriterAgent() {
        return (topic, platform, style) -> new WritingDraft(
                topic,
                """
                        这两天围绕这个话题的讨论突然升温，我最强烈的感受不是“机会来了”，而是信息密度真的变高了。

                        很多人表面上在讨论趋势，实际上焦虑的是自己会不会再次错过窗口期。真正值得写给读者看的，不是重复热点本身，而是把争议拆开：什么是情绪，什么是事实，普通人此刻能做什么。

                        如果你准备跟进这个方向，建议先从一个最具体的场景切入，再给出 2 到 3 个可执行动作，这样文章会比空泛观点更容易被收藏和转发。
                        """.trim(),
                platform,
                List.of("热点分析", "选题灵感", "内容创作"),
                82
        );
    }
}
