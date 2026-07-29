package com.ilink.test.ilinkcheck.config;

import com.ilink.test.ilinkcheck.application.agent.dto.github.RepoInfo;
import com.ilink.test.ilinkcheck.application.agent.dto.github.TrendingRepo;
import com.ilink.test.ilinkcheck.application.agent.dto.review.ReviewSuggestion;
import com.ilink.test.ilinkcheck.application.agent.dto.roadmap.DevToArticle;
import com.ilink.test.ilinkcheck.application.agent.dto.roadmap.SearchResult;
import com.ilink.test.ilinkcheck.application.agent.spi.mcp.DevToArticleProvider;
import com.ilink.test.ilinkcheck.application.agent.spi.mcp.GitHubRepoProvider;
import com.ilink.test.ilinkcheck.application.agent.spi.mcp.WebSearchProvider;
import com.ilink.test.ilinkcheck.application.agent.spi.rag.StyleGuideRetriever;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Agent 默认假实现。
 * 真实外部能力还没接完时，先靠这些 Bean 把大循环和前后链路跑通。
 */
@Configuration
public class AgentMockConfig {

    @Bean
    @ConditionalOnMissingBean(GitHubRepoProvider.class)
    public GitHubRepoProvider defaultGitHubRepoProvider() {
        return new GitHubRepoProvider() {
            @Override
            public RepoInfo fetchRepo(String owner, String repo) {
                return new RepoInfo(
                        owner,
                        repo,
                        "一个用于演示多 Agent、路由和工具编排的示例仓库。",
                        "Java",
                        1280,
                        156,
                        "README 重点介绍了如何用 Spring Boot 和 Spring AI 搭建一个可扩展的 Agent 项目。",
                        "Java / Spring Boot / Spring AI"
                );
            }

            @Override
            public List<TrendingRepo> fetchTrending(String language, String since) {
                return List.of(
                        new TrendingRepo(
                                "spring-projects",
                                "spring-ai",
                                "Spring 生态下的 AI 应用开发框架。",
                                "Java",
                                320,
                                "https://github.com/spring-projects/spring-ai"
                        ),
                        new TrendingRepo(
                                "langchain-ai",
                                "langgraph",
                                "面向多 Agent 工作流的图式编排框架。",
                                "Python",
                                280,
                                "https://github.com/langchain-ai/langgraph"
                        )
                );
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean(DevToArticleProvider.class)
    public DevToArticleProvider defaultDevToArticleProvider() {
        return (tag, limit) -> List.of(
                new DevToArticle(
                        "Getting Started with " + tag,
                        "https://dev.to/example/getting-started",
                        tag + ",tutorial",
                        320,
                        "从基础概念、运行机制和第一个 demo 入手的入门文章。"
                ),
                new DevToArticle(
                        "Practical " + tag + " Patterns",
                        "https://dev.to/example/practical-patterns",
                        tag + ",practice",
                        240,
                        "更偏实战，关注常见模式、调试和落地方式。"
                ),
                new DevToArticle(
                        tag + " in Production",
                        "https://dev.to/example/in-production",
                        tag + ",production",
                        190,
                        "讨论线上使用时的观测、约束和风险点。"
                )
        ).stream().limit(Math.max(1, limit)).toList();
    }

    @Bean
    @ConditionalOnMissingBean(WebSearchProvider.class)
    public WebSearchProvider defaultWebSearchProvider() {
        return (query, limit) -> List.of(
                new SearchResult(
                        query + " official docs",
                        "https://example.com/docs",
                        "补充官方文档入口，便于确认核心概念和 API。"
                ),
                new SearchResult(
                        query + " architecture guide",
                        "https://example.com/guide",
                        "补充一篇架构视角的延伸阅读。"
                )
        ).stream().limit(Math.max(1, limit)).toList();
    }

    @Bean
    @ConditionalOnMissingBean(StyleGuideRetriever.class)
    public StyleGuideRetriever defaultStyleGuideRetriever() {
        return draftText -> {
            if (draftText == null || draftText.isBlank()) {
                return List.of();
            }

            java.util.ArrayList<ReviewSuggestion> suggestions = new java.util.ArrayList<>();
            if (draftText.contains("Redis延迟") || draftText.contains("redis延迟")) {
                suggestions.add(new ReviewSuggestion(
                        "排版",
                        "正文前半段",
                        "检测到中英文直接贴合的写法。",
                        "建议改成 `Redis 延迟`，在中英文之间补空格。"
                ));
            }
            if (draftText.contains("```")) {
                suggestions.add(new ReviewSuggestion(
                        "代码块",
                        "代码示例",
                        "代码块可能未标明语言。",
                        "建议把代码块开头写成 ```bash 或 ```java。"
                ));
            }
            if (draftText.contains("redis")) {
                suggestions.add(new ReviewSuggestion(
                        "术语",
                        "全文",
                        "检测到 `redis` 小写写法。",
                        "专业术语建议统一写成 `Redis`。"
                ));
            }
            return suggestions;
        };
    }
}
