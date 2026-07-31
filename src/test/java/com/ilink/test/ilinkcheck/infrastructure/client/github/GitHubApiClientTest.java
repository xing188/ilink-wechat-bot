package com.ilink.test.ilinkcheck.infrastructure.client.github;

import com.ilink.test.ilinkcheck.application.agent.dto.github.RepoInfo;
import com.ilink.test.ilinkcheck.application.agent.dto.github.TrendingRepo;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GitHubApiClientTest {

    @Test
    void shouldDecodeReadmeSummaryWhenBase64ContainsLineBreaks() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GitHubApiClient client = new GitHubApiClient(builder, "https://api.github.com", "", "", false);

        server.expect(requestTo("https://api.github.com/repos/openai/codex"))
                .andRespond(withSuccess("""
                        {
                          "name": "codex",
                          "owner": {"login": "openai"},
                          "description": "Coding agent",
                          "language": "Java",
                          "stargazers_count": 42,
                          "forks_count": 7,
                          "topics": ["agent", "spring-ai"]
                        }
                        """, MediaType.APPLICATION_JSON));

        String readme = """
                # Codex

                Codex helps developers ship code faster.
                It can summarize repositories and automate workflows.
                """;
        String encoded = Base64.getMimeEncoder(20, "\n".getBytes(StandardCharsets.UTF_8))
                .encodeToString(readme.getBytes(StandardCharsets.UTF_8))
                .replace("\n", "\\n");

        server.expect(requestTo("https://api.github.com/repos/openai/codex/readme"))
                .andRespond(withSuccess("""
                        {
                          "content": "%s",
                          "encoding": "base64"
                        }
                        """.formatted(encoded), MediaType.APPLICATION_JSON));

        RepoInfo repoInfo = client.fetchRepo("openai", "codex");

        assertThat(repoInfo.readmeSummary()).contains("Codex helps developers ship code faster.");
        assertThat(repoInfo.techStack()).contains("Java").contains("agent");
        server.verify();
    }

    @Test
    void shouldFetchTrendingFromCommunityApiWhenConfigured() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GitHubApiClient client = new GitHubApiClient(
                builder,
                "https://api.github.com",
                "",
                "https://github-trending-api.vercel.app",
                false
        );

        server.expect(requestTo(startsWith("https://github-trending-api.vercel.app/repositories")))
                .andRespond(withSuccess("""
                        [
                          {
                            "author": "spring-projects",
                            "name": "spring-ai",
                            "url": "https://github.com/spring-projects/spring-ai",
                            "description": "Spring AI project",
                            "language": "Java",
                            "currentPeriodStars": 321
                          }
                        ]
                        """, MediaType.APPLICATION_JSON));

        List<TrendingRepo> repos = client.fetchTrending("java", "daily");

        assertThat(repos).hasSize(1);
        assertThat(repos.getFirst().owner()).isEqualTo("spring-projects");
        assertThat(repos.getFirst().starMetricValue()).isEqualTo(321);
        assertThat(repos.getFirst().metricType()).isEqualTo(TrendingRepo.MetricType.DAILY_STARS);
        assertThat(repos.getFirst().source()).isEqualTo(TrendingRepo.Source.COMMUNITY_TRENDING_API);
        server.verify();
    }

    @Test
    void shouldFetchTrendingFromOfficialSearchApiWhenCommunitySourceDisabled() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GitHubApiClient client = new GitHubApiClient(builder, "https://api.github.com", "", "", false);

        server.expect(requestTo(startsWith("https://api.github.com/search/repositories")))
                .andRespond(withSuccess("""
                        {
                          "total_count": 1,
                          "items": [
                            {
                              "name": "spring-ai",
                              "owner": {"login": "spring-projects"},
                              "description": "Spring AI project",
                              "language": "Java",
                              "stargazers_count": 999,
                              "html_url": "https://github.com/spring-projects/spring-ai"
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        List<TrendingRepo> repos = client.fetchTrending("java", "weekly");

        assertThat(repos).hasSize(1);
        assertThat(repos.getFirst().owner()).isEqualTo("spring-projects");
        assertThat(repos.getFirst().name()).isEqualTo("spring-ai");
        assertThat(repos.getFirst().starMetricValue()).isEqualTo(999);
        assertThat(repos.getFirst().metricType()).isEqualTo(TrendingRepo.MetricType.TOTAL_STARS);
        assertThat(repos.getFirst().source()).isEqualTo(TrendingRepo.Source.GITHUB_SEARCH_API);
        server.verify();
    }
}
