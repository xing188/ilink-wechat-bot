package com.ilink.test.ilinkcheck.application.agent.feature.digest;

import com.ilink.test.ilinkcheck.application.agent.dto.digest.DigestContent;
import com.ilink.test.ilinkcheck.application.agent.dto.github.RepoInfo;
import com.ilink.test.ilinkcheck.application.agent.dto.github.TrendingRepo;
import com.ilink.test.ilinkcheck.application.agent.dto.roadmap.DevToArticle;
import com.ilink.test.ilinkcheck.application.agent.spi.mcp.DevToArticleProvider;
import com.ilink.test.ilinkcheck.application.agent.spi.mcp.GitHubRepoProvider;
import com.ilink.test.ilinkcheck.application.tool.categories.translation.TranslationService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class DailyDigestComposeServiceTest {

    @Test
    void shouldCollectConfiguredSourcesAndRespectLimits() {
        AtomicReference<String> requestedLanguage = new AtomicReference<>();
        AtomicReference<String> requestedSince = new AtomicReference<>();
        AtomicReference<String> requestedTag = new AtomicReference<>();
        AtomicInteger requestedArticleLimit = new AtomicInteger();

        GitHubRepoProvider gitHubRepoProvider = new GitHubRepoProvider() {
            @Override
            public RepoInfo fetchRepo(String owner, String repo) {
                return null;
            }

            @Override
            public List<TrendingRepo> fetchTrending(String language, String since) {
                requestedLanguage.set(language);
                requestedSince.set(since);
                return List.of(
                        new TrendingRepo("a", "one", "repo-1", "Java", 10, "https://github.com/a/one",
                                TrendingRepo.MetricType.WEEKLY_STARS, TrendingRepo.Source.MOCK),
                        new TrendingRepo("b", "two", "repo-2", "Java", 20, "https://github.com/b/two",
                                TrendingRepo.MetricType.WEEKLY_STARS, TrendingRepo.Source.MOCK),
                        new TrendingRepo("c", "three", "repo-3", "Java", 30, "https://github.com/c/three",
                                TrendingRepo.MetricType.WEEKLY_STARS, TrendingRepo.Source.MOCK)
                );
            }
        };
        DevToArticleProvider devToArticleProvider = (tag, limit) -> {
            requestedTag.set(tag);
            requestedArticleLimit.set(limit);
            return List.of(
                    new DevToArticle("Article A", "https://dev.to/a", tag, 100, "desc-a"),
                    new DevToArticle("Article B", "https://dev.to/b", tag, 80, "desc-b")
            );
        };

        DailyDigestComposeService service = new DailyDigestComposeService(
                gitHubRepoProvider,
                devToArticleProvider,
                "rust",
                "weekly",
                2,
                "llm",
                1
        );

        DigestContent content = service.collect();

        assertThat(requestedLanguage.get()).isEqualTo("rust");
        assertThat(requestedSince.get()).isEqualTo("weekly");
        assertThat(requestedTag.get()).isEqualTo("llm");
        assertThat(requestedArticleLimit.get()).isEqualTo(1);
        assertThat(content.trendingRepos()).hasSize(2);
        assertThat(content.hotArticles()).hasSize(1);
        assertThat(content.date()).isEqualTo(LocalDate.now().toString());
    }

    @Test
    void shouldEnrichTrendingRepoWithChineseSummaryAndStripMarkdownNoise() {
        GitHubRepoProvider gitHubRepoProvider = new GitHubRepoProvider() {
            @Override
            public RepoInfo fetchRepo(String owner, String repo) {
                return new RepoInfo(
                        owner,
                        repo,
                        "Spring AI project",
                        "Java",
                        123,
                        45,
                        """
                                [![Build status](https://img.shields.io/github/actions/workflow/status/openai/codex/ci.yml)](https://github.com/openai/codex/actions)
                                This project helps build AI apps with Spring.
                                """,
                        "Java / Spring Boot",
                        ""
                );
            }

            @Override
            public List<TrendingRepo> fetchTrending(String language, String since) {
                return List.of(
                        new TrendingRepo(
                                "spring-projects",
                                "spring-ai",
                                "Trending description",
                                "Java",
                                321,
                                "https://github.com/spring-projects/spring-ai",
                                TrendingRepo.MetricType.WEEKLY_STARS,
                                TrendingRepo.Source.COMMUNITY_TRENDING_API
                        )
                );
            }
        };

        DailyDigestComposeService service = new DailyDigestComposeService(
                gitHubRepoProvider,
                (tag, limit) -> List.of(),
                "java",
                "weekly",
                3,
                "ai",
                0
        );
        service.setTranslationService(new TranslationService() {
            @Override
            public String translate(String text, String sourceLang, String targetLang) {
                return "中文概括：" + text;
            }

            @Override
            public List<String> batchTranslate(List<String> texts, String sourceLang, String targetLang) {
                return texts.stream().map(text -> "中文概括：" + text).toList();
            }
        });

        DigestContent content = service.collect();

        assertThat(content.trendingRepos()).hasSize(1);
        assertThat(content.trendingRepos().getFirst().description()).startsWith("中文概括：");
        assertThat(content.trendingRepos().getFirst().description()).contains("This project helps build AI apps with Spring.");
        assertThat(content.trendingRepos().getFirst().description()).doesNotContain("[![");
        assertThat(content.trendingRepos().getFirst().description()).doesNotContain("img.shields.io");
    }

    @Test
    void shouldBuildPreviewWithWeeklyStarsMetric() {
        DailyDigestComposeService service = new DailyDigestComposeService(
                emptyGitHubProvider(),
                (tag, limit) -> List.of(),
                "java",
                "weekly",
                3,
                "ai",
                0
        );

        DigestContent content = new DigestContent(
                List.of(new TrendingRepo(
                        "spring-projects",
                        "spring-ai",
                        "Spring AI 是一个用于构建 AI 应用的开源项目。",
                        "Java",
                        321,
                        "https://github.com/spring-projects/spring-ai",
                        TrendingRepo.MetricType.WEEKLY_STARS,
                        TrendingRepo.Source.COMMUNITY_TRENDING_API
                )),
                List.of(new DevToArticle("AI article", "https://dev.to/example/ai", "ai", 100, "一篇 AI 入门文章。")),
                "2026-07-31"
        );

        String preview = service.buildPreview("08:30", content);

        assertThat(preview).contains("已为你记录技术推送计划：每天 08:30");
        assertThat(preview).contains("推送预览（2026-07-31）");
        assertThat(preview).contains("GitHub 项目");
        assertThat(preview).contains("- spring-projects/spring-ai（本周新增 Star: 321）：Spring AI 是一个用于构建 AI 应用的开源项目。链接：https://github.com/spring-projects/spring-ai");
        assertThat(preview).contains("技术文章");
        assertThat(preview).contains("AI article");
        assertThat(preview).doesNotContain("[![");
    }

    @Test
    void shouldBuildPushMessageWithoutSubscriptionHint() {
        DailyDigestComposeService service = new DailyDigestComposeService(
                emptyGitHubProvider(),
                (tag, limit) -> List.of(),
                "java",
                "weekly",
                3,
                "ai",
                0
        );

        DigestContent content = new DigestContent(
                List.of(new TrendingRepo(
                        "spring-projects",
                        "spring-ai",
                        "Spring AI 是一个用于构建 AI 应用的开源项目。",
                        "Java",
                        999,
                        "https://github.com/spring-projects/spring-ai",
                        TrendingRepo.MetricType.TOTAL_STARS,
                        TrendingRepo.Source.GITHUB_SEARCH_API
                )),
                List.of(),
                "2026-07-31"
        );

        String pushMessage = service.buildPushMessage(content);

        assertThat(pushMessage).contains("【技术推送】2026-07-31");
        assertThat(pushMessage).contains("- spring-projects/spring-ai（当前 Star: 999）：Spring AI 是一个用于构建 AI 应用的开源项目。链接：https://github.com/spring-projects/spring-ai");
        assertThat(pushMessage).doesNotContain("已为你记录技术推送计划");
    }

    private GitHubRepoProvider emptyGitHubProvider() {
        return new GitHubRepoProvider() {
            @Override
            public RepoInfo fetchRepo(String owner, String repo) {
                return null;
            }

            @Override
            public List<TrendingRepo> fetchTrending(String language, String since) {
                return List.of();
            }
        };
    }
}
