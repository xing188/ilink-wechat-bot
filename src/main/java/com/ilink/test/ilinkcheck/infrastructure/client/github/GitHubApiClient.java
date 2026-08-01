package com.ilink.test.ilinkcheck.infrastructure.client.github;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.ilink.test.ilinkcheck.application.agent.dto.github.RepoInfo;
import com.ilink.test.ilinkcheck.application.agent.dto.github.TrendingRepo;
import com.ilink.test.ilinkcheck.application.agent.spi.mcp.GitHubRepoProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

@Component
@ConditionalOnProperty(prefix = "app.github", name = "enabled", havingValue = "true")
public class GitHubApiClient implements GitHubRepoProvider {

    private static final Logger log = LoggerFactory.getLogger(GitHubApiClient.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final RestClient restClient;
    private final RestClient trendingClient;

    @Value("${app.github.trending-path:/repositories}")
    private String trendingPath = "/repositories";

    public GitHubApiClient(RestClient.Builder restClientBuilder,
                           @Value("${app.github.api-base:https://api.github.com}") String apiBase,
                           @Value("${app.github.token:}") String token,
                           @Value("${app.github.trending-api-url:}") String trendingApiUrl,
                           @Value("${app.github.trust-local-ca:false}") boolean trustLocalCa) {
        // GitHub REST API client
        RestClient.Builder builder = restClientBuilder.baseUrl(trimTrailingSlash(apiBase));
        if (trustLocalCa) {
            // Steam++ 等工具会把 GitHub 域名改写成本机代理，Java 默认信任库不认其证书。
            log.warn("app.github.trust-local-ca=true: GitHub client will trust certificates from local TLS proxies, such as Steam++. Hostname verification stays enabled.");
            builder = builder.requestFactory(createLenientRequestFactory());
        }
        if (StringUtils.hasText(token)) {
            builder = builder.defaultHeader("Authorization", "Bearer " + token);
        }
        this.restClient = builder
                .defaultHeader("Accept", "application/vnd.github.v3+json")
                .defaultHeader("User-Agent", "ilink-wechat-bot/1.0")
                .build();

        this.trendingClient = StringUtils.hasText(trendingApiUrl)
                ? restClientBuilder
                .baseUrl(trimTrailingSlash(trendingApiUrl))
                .defaultHeader("User-Agent", "ilink-wechat-bot/1.0")
                .build()
                : null;
    }

    @Override
    public RepoInfo fetchRepo(String owner, String repo) {
        if (!StringUtils.hasText(owner) || !StringUtils.hasText(repo)) {
            throw new GitHubClientException(GitHubClientException.Type.INVALID_PARAMETER,
                    "owner and repo must not be blank");
        }
        try {
            ApiRepoResponse response = restClient.get()
                    .uri("/repos/{owner}/{repo}", owner, repo)
                    .retrieve()
                    .body(ApiRepoResponse.class);
            if (response == null) {
                throw new GitHubClientException(GitHubClientException.Type.UPSTREAM_ERROR,
                        "GitHub API returned empty body");
            }

            return new RepoInfo(
                    response.owner() != null ? response.owner().login() : owner,
                    response.name(),
                    response.description(),
                    response.language(),
                    response.stars(),
                    response.forks(),
                    fetchReadmeSummary(owner, repo),
                    detectTechStack(response.language(), response.topics())
            );
        } catch (RestClientResponseException e) {
            throw translateException("fetchRepo", e);
        }
    }

    @Override
    public List<TrendingRepo> fetchTrending(String language, String since) {
        if (trendingClient != null) {
            try {
                return fetchTrendingFromCommunityApi(language, since);
            } catch (Exception e) {
                log.warn("Community trending API failed, fallback to official search API", e);
            }
        }
        return fetchTrendingFromSearch(language, since);
    }

    private List<TrendingRepo> fetchTrendingFromCommunityApi(String language, String since) {
        JsonNode response = trendingClient.get()
                .uri(uriBuilder -> {
                    var builder = uriBuilder.path(trendingPath);
                    if (StringUtils.hasText(since)) {
                        builder = builder.queryParam("since", since);
                    }
                    if (StringUtils.hasText(language) && !"all".equalsIgnoreCase(language)) {
                        builder = builder.queryParam("language", language);
                    }
                    return builder.build();
                })
                .retrieve()
                .body(JsonNode.class);

        JsonNode items = response != null && response.isArray() ? response : response != null ? response.path("data") : null;
        if (items == null || !items.isArray() || items.size() == 0) {
            return Collections.emptyList();
        }

        List<TrendingRepo> repos = new ArrayList<>();
        for (JsonNode item : items) {
            String url = item.path("url").asText("");
            String owner = firstText(item, "author", "username");
            String name = firstText(item, "name", "reponame");
            int stars = item.hasNonNull("currentPeriodStars")
                    ? item.path("currentPeriodStars").asInt(0)
                    : item.path("starsToday").asInt(0);
            repos.add(new TrendingRepo(
                    StringUtils.hasText(owner) ? owner : parseOwner(url),
                    StringUtils.hasText(name) ? name : "unknown",
                    item.path("description").asText(null),
                    item.path("language").asText(null),
                    stars,
                    url,
                    TrendingRepo.MetricType.DAILY_STARS,
                    TrendingRepo.Source.COMMUNITY_TRENDING_API
            ));
        }
        return repos;
    }

    private String firstText(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode value = node.get(fieldName);
            if (value != null && value.isTextual() && StringUtils.hasText(value.asText())) {
                return value.asText();
            }
        }
        return "";
    }

    private List<TrendingRepo> fetchTrendingFromSearch(String language, String since) {
        try {
            StringBuilder query = new StringBuilder("sort:stars");

            LocalDate sinceDate = switch (since != null ? since : "daily") {
                case "weekly" -> LocalDate.now().minusDays(7);
                case "monthly" -> LocalDate.now().minusDays(30);
                default -> LocalDate.now().minusDays(1);
            };
            query.append(" created:>=").append(sinceDate.format(DATE_FMT));

            if (StringUtils.hasText(language) && !"all".equalsIgnoreCase(language)) {
                query.append(" language:").append(language);
            }

            ApiSearchResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/search/repositories")
                            .queryParam("q", query.toString())
                            .queryParam("sort", "stars")
                            .queryParam("order", "desc")
                            .queryParam("per_page", 10)
                            .build())
                    .retrieve()
                    .body(ApiSearchResponse.class);

            if (response == null || response.items() == null) {
                return Collections.emptyList();
            }

            return response.items().stream()
                    .map(item -> new TrendingRepo(
                            item.owner() != null ? item.owner().login() : "unknown",
                            item.name(),
                            item.description(),
                            item.language(),
                            item.stars(),
                            item.htmlUrl(),
                            TrendingRepo.MetricType.TOTAL_STARS,
                            TrendingRepo.Source.GITHUB_SEARCH_API
                    ))
                    .toList();
        } catch (RestClientResponseException e) {
            throw translateException("fetchTrending", e);
        }
    }

    private String fetchReadmeSummary(String owner, String repo) {
        try {
            ApiReadmeResponse readmeResp = restClient.get()
                    .uri("/repos/{owner}/{repo}/readme", owner, repo)
                    .retrieve()
                    .body(ApiReadmeResponse.class);
            if (readmeResp == null || !StringUtils.hasText(readmeResp.content())) {
                return "";
            }

            byte[] decoded = Base64.getMimeDecoder().decode(readmeResp.content());
            String text = new String(decoded, StandardCharsets.UTF_8);
            String[] lines = text.split("\n");
            StringBuilder summary = new StringBuilder();
            for (String line : lines) {
                String trimmed = line.strip();
                if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("![")
                        || trimmed.startsWith("<") || trimmed.startsWith("```")) {
                    continue;
                }
                summary.append(trimmed).append(" ");
                if (summary.length() > 300) {
                    break;
                }
            }
            String result = summary.toString().strip();
            return result.length() > 300 ? result.substring(0, 300) + "..." : result;
        } catch (Exception e) {
            log.warn("Failed to decode README summary for repo={}/{}", owner, repo, e);
            return "";
        }
    }

    private String detectTechStack(String primaryLanguage, List<String> topics) {
        StringBuilder sb = new StringBuilder();
        if (StringUtils.hasText(primaryLanguage)) {
            sb.append(primaryLanguage);
        }
        if (topics != null && !topics.isEmpty()) {
            List<String> techTopics = topics.stream()
                    .filter(t -> !t.equalsIgnoreCase(primaryLanguage))
                    .limit(5)
                    .toList();
            for (String topic : techTopics) {
                sb.append(sb.isEmpty() ? "" : ", ").append(topic);
            }
        }
        return sb.toString();
    }

    private String parseOwner(String url) {
        if (!StringUtils.hasText(url)) {
            return "unknown";
        }
        String[] parts = url.replace("https://github.com/", "").split("/");
        return parts.length > 0 ? parts[0] : "unknown";
    }

    private GitHubClientException translateException(String operation, RestClientResponseException e) {
        String message = e.getMessage();
        try {
            ApiErrorResponse error = e.getResponseBodyAs(ApiErrorResponse.class);
            if (error != null && StringUtils.hasText(error.message())) {
                message = error.message();
            }
        } catch (Exception ignored) {
        }
        return switch (e.getStatusCode().value()) {
            case 400 -> new GitHubClientException(GitHubClientException.Type.INVALID_PARAMETER, message, e);
            case 404 -> new GitHubClientException(GitHubClientException.Type.NOT_FOUND, message, e);
            case 403 -> new GitHubClientException(GitHubClientException.Type.RATE_LIMITED, message, e);
            case 503 -> new GitHubClientException(GitHubClientException.Type.SERVICE_UNAVAILABLE, message, e);
            default -> new GitHubClientException(GitHubClientException.Type.UPSTREAM_ERROR,
                    "GitHub " + operation + " failed: " + message, e);
        };
    }

    private String trimTrailingSlash(String value) {
        if (value == null) {
            return null;
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private ClientHttpRequestFactory createLenientRequestFactory() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .sslContext(createLenientSslContext())
                .build();
        return new JdkClientHttpRequestFactory(httpClient);
    }

    private SSLContext createLenientSslContext() {
        try {
            TrustManager[] trustManagers = new TrustManager[] {
                    new X509TrustManager() {
                        @Override
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[0];
                        }

                        @Override
                        public void checkClientTrusted(X509Certificate[] chain, String authType) {
                        }

                        @Override
                        public void checkServerTrusted(X509Certificate[] chain, String authType) {
                        }
                    }
            };
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, trustManagers, new SecureRandom());
            return context;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to initialize GitHub client SSL context", e);
        }
    }

    // --- GitHub REST API response DTOs ---

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ApiRepoResponse(
            Long id,
            String name,
            @JsonProperty("full_name") String fullName,
            Owner owner,
            String description,
            String language,
            @JsonProperty("stargazers_count") int stars,
            @JsonProperty("forks_count") int forks,
            @JsonProperty("html_url") String htmlUrl,
            List<String> topics
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Owner(String login, Long id) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ApiReadmeResponse(
            String content,
            String encoding,
            @JsonProperty("download_url") String downloadUrl
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ApiSearchResponse(
            @JsonProperty("total_count") int totalCount,
            List<ApiSearchItem> items
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ApiSearchItem(
            Long id,
            String name,
            @JsonProperty("full_name") String fullName,
            Owner owner,
            String description,
            String language,
            @JsonProperty("stargazers_count") int stars,
            @JsonProperty("html_url") String htmlUrl
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ApiErrorResponse(
            String message,
            @JsonProperty("documentation_url") String documentationUrl
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ApiTrendingItem(
            String author,
            String name,
            String url,
            String description,
            String language,
            @JsonProperty("currentPeriodStars") int starsToday
    ) {}
}
