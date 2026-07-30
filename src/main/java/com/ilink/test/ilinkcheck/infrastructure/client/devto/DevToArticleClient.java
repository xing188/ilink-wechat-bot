package com.ilink.test.ilinkcheck.infrastructure.client.devto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ilink.test.ilinkcheck.application.agent.dto.roadmap.DevToArticle;
import com.ilink.test.ilinkcheck.application.agent.spi.mcp.DevToArticleProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
@Slf4j
public class DevToArticleClient implements DevToArticleProvider {

    private static final String BASE_URL = "https://dev.to/api";
    private static final int DEFAULT_LIMIT = 5;

    private final RestClient restClient;

    public DevToArticleClient(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.baseUrl(BASE_URL).build();
    }

    @Override
    public List<DevToArticle> fetchByTag(String tag, int limit) {
        int resolvedLimit = limit > 0 ? Math.min(limit, 10) : DEFAULT_LIMIT;

        try {
            List<DevToArticleResponse> responses = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/articles")
                            .queryParam("tag", tag)
                            .queryParam("per_page", resolvedLimit)
                            .queryParam("top", 1)
                            .build())
                    .retrieve()
                    .body(DevToArticleList.class);

            if (responses == null || responses.isEmpty()) {
                log.info("Dev.to: tag={} 暂无匹配文章", tag);
                return List.of();
            }

            var result = responses.stream()
                    .map(this::toArticle)
                    .filter(a -> a != null && a.title() != null)
                    .toList();

            log.info("Dev.to: tag={}, 获取到 {} 篇文章", tag, result.size());
            return result;
        }
        catch (Exception e) {
            log.warn("Dev.to API 调用失败: tag={}", tag, e);
            return List.of();
        }
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
