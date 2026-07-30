package com.ilink.test.ilinkcheck.infrastructure.client.search;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ilink.test.ilinkcheck.application.agent.dto.roadmap.SearchResult;
import com.ilink.test.ilinkcheck.application.agent.spi.mcp.WebSearchProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
@Slf4j
public class SerpApiWebSearchClient implements WebSearchProvider {

    private static final String BASE_URL = "https://serpapi.com";
    private static final int DEFAULT_LIMIT = 5;
    private static final int MAX_LIMIT = 20;

    private final RestClient restClient;
    private final String apiKey;

    public SerpApiWebSearchClient(RestClient.Builder restClientBuilder,
                                   @Value("${app.search.serpapi-key}") String apiKey) {
        this.restClient = restClientBuilder.baseUrl(BASE_URL).build();
        this.apiKey = apiKey;
    }

    @Override
    public List<SearchResult> search(String query, int limit) {
        if (!StringUtils.hasText(query)) {
            return List.of();
        }

        int resolvedLimit = Math.max(1, Math.min(limit > 0 ? limit : DEFAULT_LIMIT, MAX_LIMIT));

        try {
            SerpApiResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/search")
                            .queryParam("q", query.trim())
                            .queryParam("api_key", apiKey)
                            .queryParam("engine", "google")
                            .queryParam("num", resolvedLimit)
                            .queryParam("source", "web")
                            .build())
                    .retrieve()
                    .body(SerpApiResponse.class);

            if (response == null || response.organicResults == null || response.organicResults.isEmpty()) {
                log.info("SerpApi: query={}, 暂无匹配结果", query);
                return List.of();
            }

            if (response.searchMetadata != null
                    && !"Success".equalsIgnoreCase(response.searchMetadata.status)) {
                log.warn("SerpApi: query={}, 状态异常={}", query, response.searchMetadata.status);
                return List.of();
            }

            var result = response.organicResults.stream()
                    .filter(item -> StringUtils.hasText(item.title))
                    .map(item -> new SearchResult(
                            item.title.trim(),
                            StringUtils.hasText(item.link) ? item.link.trim() : "",
                            item.snippet != null ? item.snippet.trim() : ""
                    ))
                    .toList();

            log.info("SerpApi: query={}, 获取到 {} 条结果", query, result.size());
            return result;
        }
        catch (Exception e) {
            log.warn("SerpApi 调用失败: query={}", query, e);
            return List.of();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SerpApiResponse(
            @JsonProperty("search_metadata") SearchMetadata searchMetadata,
            @JsonProperty("organic_results") List<OrganicResult> organicResults
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SearchMetadata(String status) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OrganicResult(
            String title,
            String link,
            String snippet
    ) {}
}
