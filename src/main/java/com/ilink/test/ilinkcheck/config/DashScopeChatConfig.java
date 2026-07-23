package com.ilink.test.ilinkcheck.config;

import com.ilink.test.ilinkcheck.infrastructure.persistence.file.FileChatMemory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class DashScopeChatConfig {

    @Value("${spring.ai.openai.api-key}")
    private String apiKey;

    @Value("${spring.ai.openai.base-url}")
    private String baseUrl;

    @Value("${spring.ai.openai.chat.model}")
    private String model;

    @Value("${spring.ai.openai.chat.temperature:0.7}")
    private Double temperature;

    @Value("${spring.ai.openai.chat.max-tokens:512}")
    private Integer maxTokens;

    @Bean
    public OpenAiChatModel dashScopeCompatibleChatModel() {
        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(trimTrailingSlash(baseUrl))
                .apiKey(apiKey)
                .completionsPath("/chat/completions")
                .build();

        OpenAiChatOptions chatOptions = OpenAiChatOptions.builder()
                .model(model)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .build();

        return OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(chatOptions)
                .build();
    }

    @Bean
    public ChatClient.Builder chatClientBuilder(OpenAiChatModel dashScopeCompatibleChatModel) {
        return ChatClient.builder(dashScopeCompatibleChatModel);
    }

    @Bean
    public ChatClient conversationChatClient(ChatClient.Builder chatClientBuilder, ChatMemory chatMemory) {
        return chatClientBuilder.clone()
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }

    @Bean
    public ChatMemory chatMemory() {
        return new FileChatMemory();
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    private String trimTrailingSlash(String value) {
        if (value == null) return null;
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
