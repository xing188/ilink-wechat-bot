package com.ilink.test.ilinkcheck.infrastructure.news;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ilink.test.ilinkcheck.model.NewsResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;

@Component
public class TianApiNewsClient {

    private static final String DEFAULT_BASE_URL = "http://api.tianapi.com";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;

    public TianApiNewsClient(RestClient.Builder restClientBuilder,
                              ObjectMapper objectMapper,
                              @Value("${app.tianapi.base-url:" + DEFAULT_BASE_URL + "}") String baseUrl,
                              @Value("${app.tianapi.key}") String apiKey) {
        this.restClient = restClientBuilder
                .baseUrl(trimTrailingSlash(baseUrl))
                .build();
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
    }

    public NewsResult query(String keyword, String category, int num, int page) {
        try {
            String path = resolvePath(category);

            ApiNewsResponse response = restClient.get()
                    .uri(uriBuilder -> {
                        var builder = uriBuilder.path(path)
                                .queryParam("key", apiKey)
                                .queryParam("num", num)
                                .queryParam("page", page);
                        if (StringUtils.hasText(keyword)) {
                            builder = builder.queryParam("word", keyword);
                        }
                        return builder.build();
                    })
                    .retrieve()
                    .body(ApiNewsResponse.class);

            if (response == null) {
                throw new NewsClientException(NewsClientException.Type.UPSTREAM_ERROR,
                        "News API returned an empty body");
            }

            if (response.code() != 200) {
                String errorMsg = response.msg() != null ? response.msg() : "unknown error";
                if (response.code() == 160) {
                    throw new NewsClientException(NewsClientException.Type.SERVICE_UNAVAILABLE,
                            "该新闻分类尚未申请，请前往天行数据控制台申请。");
                }
                if (response.code() == 250) {
                    return NewsResult.empty();
                }
                throw new NewsClientException(NewsClientException.Type.UPSTREAM_ERROR,
                        "News API error code " + response.code() + ": " + errorMsg);
            }

            return response.toNewsResult();
        }
        catch (RestClientResponseException e) {
            throw translateException(e);
        }
        catch (NewsClientException e) {
            throw e;
        }
        catch (Exception e) {
            throw new NewsClientException(NewsClientException.Type.UPSTREAM_ERROR,
                    "News API call failed", e);
        }
    }

    private String resolvePath(String category) {
        if (!StringUtils.hasText(category)) {
            return "/social/";
        }
        return switch (category.trim().toLowerCase()) {
            case "domestic", "国内" -> "/guonei/";
            case "social", "社会" -> "/social/";
            case "international", "国际" -> "/world/";
            case "tech", "科技" -> "/keji/";
            case "entertainment", "娱乐" -> "/huabian/";
            case "sports", "体育" -> "/tiyu/";
            case "military", "军事" -> "/military/";
            default -> "/social/";
        };
    }

    private NewsClientException translateException(RestClientResponseException e) {
        String message = e.getMessage();
        try {
            ApiErrorResponse error = objectMapper.readValue(e.getResponseBodyAsString(), ApiErrorResponse.class);
            if (error != null && StringUtils.hasText(error.message())) {
                message = error.message();
            }
        }
        catch (Exception ignored) {
        }

        return switch (e.getStatusCode().value()) {
            case 400 -> new NewsClientException(NewsClientException.Type.INVALID_PARAMETER, message, e);
            case 404 -> new NewsClientException(NewsClientException.Type.NOT_FOUND, message, e);
            case 503 -> new NewsClientException(NewsClientException.Type.SERVICE_UNAVAILABLE, message, e);
            default -> new NewsClientException(NewsClientException.Type.UPSTREAM_ERROR, message, e);
        };
    }

    private String trimTrailingSlash(String value) {
        if (value == null) {
            return null;
        }
        if (value.endsWith("/")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ApiNewsResponse(
            int code,
            String msg,
            @JsonProperty("newslist")
            List<ApiNewsItem> newslist
    ) {

        NewsResult toNewsResult() {
            if (newslist == null || newslist.isEmpty()) {
                return NewsResult.empty();
            }

            List<NewsResult.NewsItem> items = newslist.stream()
                    .map(item -> new NewsResult.NewsItem(
                            item.id(),
                            item.title(),
                            item.url(),
                            item.ctime(),
                            item.description(),
                            item.picUrl(),
                            item.source()
                    ))
                    .toList();

            return new NewsResult(items, items.size(), 1);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ApiNewsItem(
            String id,
            String title,
            String url,
            String ctime,
            String description,
            @JsonProperty("picUrl")
            String picUrl,
            String source
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ApiErrorResponse(
            String code,
            String message
    ) {
    }
}
