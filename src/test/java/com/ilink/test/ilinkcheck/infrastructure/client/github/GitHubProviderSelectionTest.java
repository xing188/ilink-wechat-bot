package com.ilink.test.ilinkcheck.infrastructure.client.github;

import com.ilink.test.ilinkcheck.application.agent.spi.mcp.GitHubRepoProvider;
import com.ilink.test.ilinkcheck.config.AgentMockConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

class GitHubProviderSelectionTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class)
            .withPropertyValues("app.github.api-base=https://api.github.com");

    @Test
    void shouldUseRealProviderWhenGithubEnabled() {
        contextRunner.withPropertyValues("app.github.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(GitHubRepoProvider.class);
                    assertThat(context.getBean(GitHubRepoProvider.class)).isInstanceOf(GitHubApiClient.class);
                });
    }

    @Configuration(proxyBeanMethods = false)
    @Import(AgentMockConfig.class)
    @ComponentScan(basePackageClasses = {GitHubApiClient.class})
    static class TestConfig {

        @Bean
        RestClient.Builder restClientBuilder() {
            return RestClient.builder();
        }
    }
}
