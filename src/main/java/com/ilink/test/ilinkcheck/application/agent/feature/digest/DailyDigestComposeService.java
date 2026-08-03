package com.ilink.test.ilinkcheck.application.agent.feature.digest;

import com.ilink.test.ilinkcheck.application.agent.dto.digest.DigestContent;
import com.ilink.test.ilinkcheck.application.agent.dto.github.RepoInfo;
import com.ilink.test.ilinkcheck.application.agent.dto.github.TrendingRepo;
import com.ilink.test.ilinkcheck.application.agent.dto.roadmap.DevToArticle;
import com.ilink.test.ilinkcheck.application.agent.spi.mcp.DevToArticleProvider;
import com.ilink.test.ilinkcheck.application.agent.spi.mcp.GitHubRepoProvider;
import com.ilink.test.ilinkcheck.application.tool.categories.translation.TranslationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * 技术推送内容聚合服务。
 * 负责把 GitHub 周报和技术文章整理成用户可读的中文摘要。
 */
@Service
public class DailyDigestComposeService {

    private final GitHubRepoProvider gitHubRepoProvider;
    private final DevToArticleProvider devToArticleProvider;
    private final String githubLanguage;
    private final String githubSince;
    private final int githubLimit;
    private final String devtoTag;
    private final int devtoLimit;
    private TranslationService translationService;

    public DailyDigestComposeService(GitHubRepoProvider gitHubRepoProvider,
                                     DevToArticleProvider devToArticleProvider,
                                     @Value("${app.digest.github.language:java}") String githubLanguage,
                                     @Value("${app.digest.github.since:weekly}") String githubSince,
                                     @Value("${app.digest.github.limit:5}") int githubLimit,
                                     @Value("${app.digest.devto.tag:ai}") String devtoTag,
                                     @Value("${app.digest.devto.limit:0}") int devtoLimit) {
        this.gitHubRepoProvider = gitHubRepoProvider;
        this.devToArticleProvider = devToArticleProvider;
        this.githubLanguage = normalizeText(githubLanguage);
        this.githubSince = normalizeText(githubSince);
        this.githubLimit = Math.max(1, githubLimit);
        this.devtoTag = normalizeText(devtoTag);
        this.devtoLimit = Math.max(0, devtoLimit);
    }

    @Autowired(required = false)
    public void setTranslationService(TranslationService translationService) {
        this.translationService = translationService;
    }

    /**
     * 收集当期推送内容。
     */
    public DigestContent collect() {
        List<TrendingRepo> trendingRepos = enrichTrendingRepos(limit(
                gitHubRepoProvider.fetchTrending(githubLanguage, githubSince),
                githubLimit
        ));
        List<DevToArticle> hotArticles = devtoLimit <= 0
                ? List.of()
                : limit(devToArticleProvider.fetchByTag(devtoTag, devtoLimit), devtoLimit);
        return new DigestContent(trendingRepos, hotArticles, LocalDate.now().toString());
    }

    /**
     * 生成订阅成功后的预览文案。
     */
    public String buildPreview(String scheduleTime, DigestContent content) {
        return renderDigest(
                "已为你记录技术推送计划：" + previewScheduleLabel(scheduleTime),
                "推送预览（" + defaultText(safeContent(content).date(), LocalDate.now().toString()) + "）：",
                safeContent(content),
                true
        );
    }

    /**
     * 生成真正定时推送给用户的正文。
     */
    public String buildPushMessage(DigestContent content) {
        DigestContent safeContent = safeContent(content);
        return renderDigest(
                "【技术推送】" + defaultText(safeContent.date(), LocalDate.now().toString()),
                null,
                safeContent,
                false
        );
    }

    private String renderDigest(String title, String previewHeader, DigestContent content, boolean includeHint) {
        StringBuilder sb = new StringBuilder();
        sb.append(title).append(System.lineSeparator()).append(System.lineSeparator());
        if (StringUtils.hasText(previewHeader)) {
            sb.append(previewHeader).append(System.lineSeparator()).append(System.lineSeparator());
        }
        sb.append("GitHub 项目：").append(System.lineSeparator());
        sb.append(formatTrendingRepos(content.trendingRepos()));
        if (content.hotArticles() != null && !content.hotArticles().isEmpty()) {
            sb.append(System.lineSeparator()).append(System.lineSeparator());
            sb.append("技术文章：").append(System.lineSeparator());
            sb.append(formatArticles(content.hotArticles()));
        }
        if (includeHint) {
            sb.append(System.lineSeparator()).append(System.lineSeparator());
            sb.append("后续我会按这个计划自动推送；如果你想修改时间，直接告诉我新的计划即可。");
        }
        return sb.toString().trim();
    }

    private DigestContent safeContent(DigestContent content) {
        return content == null
                ? new DigestContent(List.of(), List.of(), LocalDate.now().toString())
                : content;
    }

    private List<TrendingRepo> enrichTrendingRepos(List<TrendingRepo> repos) {
        if (repos == null || repos.isEmpty()) {
            return List.of();
        }

        return repos.stream()
                .map(this::enrichTrendingRepo)
                .filter(Objects::nonNull)
                .toList();
    }

    private TrendingRepo enrichTrendingRepo(TrendingRepo repo) {
        try {
            RepoInfo repoInfo = gitHubRepoProvider.fetchRepo(repo.owner(), repo.name());
            if (repoInfo == null) {
                return repo;
            }

            String summary = buildProjectSummary(repo, repoInfo);
            String language = defaultText(repoInfo.language(), repo.language());
            String url = defaultText(repo.url(), buildGithubUrl(repo.owner(), repo.name()));
            return new TrendingRepo(
                    defaultText(repoInfo.owner(), repo.owner()),
                    defaultText(repoInfo.name(), repo.name()),
                    summary,
                    language,
                    repo.starMetricValue(),
                    url,
                    repo.metricType(),
                    repo.source()
            );
        } catch (Exception e) {
            // 单个仓库补摘要失败时，不影响整份周报。
            return repo;
        }
    }

    private String buildProjectSummary(TrendingRepo repo, RepoInfo repoInfo) {
        String rawSummary = firstText(repoInfo.readmeSummary(), repoInfo.description(), repo.description());
        String summary = buildChineseSummary(rawSummary);
        if (!StringUtils.hasText(summary)) {
            summary = buildFallbackChineseSummary(repo, repoInfo);
        }
        return summary;
    }

    private String buildChineseSummary(String text) {
        String summary = sanitizeSummaryText(text);
        if (!StringUtils.hasText(summary)) {
            return "";
        }

        if (translationService != null && containsLatinLetters(summary)) {
            String translated = translateToChinese(summary);
            if (StringUtils.hasText(translated)) {
                summary = translated;
            }
        }

        summary = sanitizeSummaryText(summary);
        summary = firstSentence(summary);
        return sanitizeSummaryText(summary);
    }

    private String buildGithubUrl(String owner, String repo) {
        return "https://github.com/" + owner + "/" + repo;
    }

    private String formatTrendingRepos(List<TrendingRepo> repos) {
        if (repos == null || repos.isEmpty()) {
            return "- 暂无 GitHub 项目";
        }

        return repos.stream()
                .map(item -> "- " + item.owner() + "/" + item.name()
                        + "（" + item.formattedStarMetric() + "）："
                        + normalizeProjectIntro(defaultText(item.description(), "这是一个值得关注的开源项目"))
                        + "。链接：" + defaultText(item.url(), buildGithubUrl(item.owner(), item.name())))
                .reduce((a, b) -> a + System.lineSeparator() + b)
                .orElse("- 暂无 GitHub 项目");
    }

    private String formatArticles(List<DevToArticle> articles) {
        if (articles == null || articles.isEmpty()) {
            return "- 暂无技术文章";
        }

        return articles.stream()
                .map(item -> "- " + item.title()
                        + "（点赞: " + item.positiveReactions() + "）："
                        + defaultText(buildChineseSummary(item.description()), "这是一篇值得快速浏览的技术文章"))
                .reduce((a, b) -> a + System.lineSeparator() + b)
                .orElse("- 暂无技术文章");
    }

    private <T> List<T> limit(List<T> source, int maxSize) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        return source.stream()
                .filter(Objects::nonNull)
                .limit(Math.max(1, maxSize))
                .toList();
    }

    private String normalizeText(String text) {
        return text == null ? "" : text.trim();
    }

    private String defaultText(String text, String fallback) {
        return StringUtils.hasText(text) ? text.trim() : fallback;
    }

    private String firstText(String... texts) {
        if (texts == null) {
            return "";
        }
        for (String text : texts) {
            if (StringUtils.hasText(text)) {
                return text.trim();
            }
        }
        return "";
    }

    private String sanitizeSummaryText(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String cleaned = text.replaceAll("\\[!\\[[^\\]]*\\]\\([^)]*\\)\\]\\([^)]*\\)", "");
        cleaned = cleaned.replaceAll("!\\[[^\\]]*\\]\\([^)]*\\)", "");
        cleaned = cleaned.replaceAll("\\[([^\\]]+)\\]\\(([^)]+)\\)", "$1");
        cleaned = cleaned.replaceAll("<[^>]+>", "");
        cleaned = cleaned.replaceAll("^[\\s\\-•*\\d.]+", "");
        cleaned = cleaned.replaceAll("\\s+", " ").trim();
        return cleaned;
    }

    private String firstSentence(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String[] parts = text.split("(?<=[。！？!?\\.])\\s*");
        return parts.length == 0 ? text.trim() : parts[0].trim();
    }

    private boolean containsChinese(String text) {
        return StringUtils.hasText(text) && text.matches(".*[\\u4e00-\\u9fff].*");
    }

    private boolean containsLatinLetters(String text) {
        return StringUtils.hasText(text) && text.matches(".*[A-Za-z].*");
    }

    private String buildFallbackChineseSummary(TrendingRepo repo, RepoInfo repoInfo) {
        String language = firstText(repoInfo.language(), repo.language());
        if (StringUtils.hasText(language)) {
            return "这是一个用 " + language + " 构建的开源项目，可进一步查看仓库说明。";
        }
        return "这是一个值得关注的开源项目，可进一步查看仓库说明。";
    }

    private String normalizeProjectIntro(String text) {
        if (!StringUtils.hasText(text)) {
            return "这是一个值得关注的开源项目";
        }
        return text.replaceAll("[。！？!?\\.]+$", "").trim();
    }

    private String translateToChinese(String text) {
        try {
            String translated = translationService.translate(text, "English", "Chinese");
            return StringUtils.hasText(translated) ? translated.trim() : "";
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 预览文案里优先展示“计划标签”。
     * 如果用户只说了一个裸时间点，这里会自动补成“每天 HH:mm”。
     */
    private String previewScheduleLabel(String scheduleTime) {
        String normalized = defaultText(scheduleTime, "未设置");
        if (normalized.matches("\\d{1,2}:\\d{2}")) {
            return "每天 " + normalized;
        }
        return normalized;
    }
}
