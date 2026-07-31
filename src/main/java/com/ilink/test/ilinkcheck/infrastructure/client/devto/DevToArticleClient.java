package com.ilink.test.ilinkcheck.infrastructure.client.devto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ilink.test.ilinkcheck.application.agent.dto.roadmap.DevToArticle;
import com.ilink.test.ilinkcheck.application.agent.spi.mcp.DevToArticleProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class DevToArticleClient implements DevToArticleProvider {

    private static final String BASE_URL = "https://dev.to/api";
    private static final int DEFAULT_LIMIT = 5;

    /**
     * 中文主题 → Dev.to 英文标签映射。
     * Dev.to 是英文社区，标签全为英文小写；中文主题直接查必然 0 结果，
     * 这里把常见中文主题翻成对应标签。不在表中的中文主题保持原样（查不到时走空结果兜底）。
     */
    private static final Map<String, String> TAG_ALIASES = Map.ofEntries(
            Map.entry("微服务", "microservices"),
            Map.entry("机器学习", "machinelearning"),
            Map.entry("深度学习", "deeplearning"),
            Map.entry("人工智能", "ai"),
            Map.entry("大模型", "llm"),
            Map.entry("前端", "frontend"),
            Map.entry("后端", "backend"),
            Map.entry("数据库", "database"),
            Map.entry("容器", "docker"),
            Map.entry("云原生", "kubernetes"),
            Map.entry("爬虫", "webscraping"),
            Map.entry("算法", "algorithms")
    );

    private final RestClient restClient;

    public DevToArticleClient(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder
                .baseUrl(BASE_URL)
                .defaultHeader("User-Agent", "TechPilot-Bot/1.0 (ilink-wechat-bot)")
                .build();
    }

    @Override
    public List<DevToArticle> fetchByTag(String tag, int limit) {
        int resolvedLimit = limit > 0 ? Math.min(limit, 10) : DEFAULT_LIMIT;
        String normalizedTag = normalizeTag(tag);

        // 先查"最近 30 天热门"（质量高）；空则退化为"按最新"再查一次（覆盖冷门标签）
        List<DevToArticle> result = doFetch(normalizedTag, resolvedLimit, 30);
        if (result.isEmpty()) {
            result = doFetch(normalizedTag, resolvedLimit, 0);
        }
        log.info("Dev.to: tag={}（原始输入 {}）, 获取到 {} 篇文章", normalizedTag, tag, result.size());
        return result;
    }

    /**
     * 执行一次 Dev.to 文章查询。
     *
     * @param topDays 大于 0 时附加 top 参数（含义：最近 N 天内最热门）；0 表示不加，按最新返回
     */
    private List<DevToArticle> doFetch(String tag, int limit, int topDays) {
        try {
            List<DevToArticleResponse> responses = restClient.get()
                    .uri(uriBuilder -> {
                        var b = uriBuilder.path("/articles")
                                .queryParam("tag", tag)
                                .queryParam("per_page", limit);
                        if (topDays > 0) {
                            b = b.queryParam("top", topDays);
                        }
                        return b.build();
                    })
                    .retrieve()
                    .body(DevToArticleList.class);

            if (responses == null || responses.isEmpty()) {
                return List.of();
            }

            return responses.stream()
                    .map(this::toArticle)
                    .filter(a -> a != null && a.title() != null)
                    .toList();
        }
        catch (Exception e) {
            log.warn("Dev.to API 调用失败: tag={}, topDays={}", tag, topDays, e);
            return List.of();
        }
    }

    /**
     * 标签归一化：中文主题先查别名表翻成英文标签；
     * 英文主题转小写并去空格（Dev.to 标签均为小写无空格，如 Spring AI → springai）。
     */
    private static String normalizeTag(String tag) {
        String t = tag == null ? "" : tag.trim();
        String mapped = TAG_ALIASES.get(t);
        if (mapped != null) {
            return mapped;
        }
        return t.toLowerCase().replace(" ", "");
    }

    private DevToArticle toArticle(DevToArticleResponse r) {
        return new DevToArticle(
                r.title != null ? r.title : "",
                r.url != null ? r.url : "",
                r.tagList != null ? String.join(",", r.tagList) : "",
                r.positiveReactionsCount,
                r.description != null ? r.description : ""
        );
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record DevToArticleResponse(
            String title,
            String url,
            @JsonProperty("tag_list") List<String> tagList,
            @JsonProperty("positive_reactions_count") int positiveReactionsCount,
            String description
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class DevToArticleList extends java.util.ArrayList<DevToArticleResponse> {}
}
