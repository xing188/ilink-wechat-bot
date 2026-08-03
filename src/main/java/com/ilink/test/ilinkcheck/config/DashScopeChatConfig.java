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
import org.springframework.context.annotation.Primary;
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

    /**
     * 「快模型」名称：用于学习路线规划、翻译这类结构化、确定性高的短任务。
     * 默认与主对话模型一致（保证任何账号都能跑通）；在 application-ai.yml 里
     * 显式配成 qwen-flash / qwen-turbo 可把耗时从 15~30s 压到 3~5s。
     */
    @Value("${app.agent.fast-model:${spring.ai.openai.chat.model}}")
    private String fastModel;

    /** 快模型输出上限。路线 JSON / 翻译结果都不长，1500 足够且能更早收敛。 */
    @Value("${app.agent.fast-max-tokens:1500}")
    private Integer fastMaxTokens;

    /** 快模型温度。规划与翻译要稳定可复现，取低温。 */
    @Value("${app.agent.fast-temperature:0.2}")
    private Double fastTemperature;

    @Bean
    @Primary
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

    /**
     * 语义路由专用 ChatClient（意图路由 / 工具选择）。
     * 路由输出是"短分类结果"，用快模型可把响应压到 1~2s，
     * 避免占用主对话模型导致每条消息等待数秒。
     */
    @Bean
    public ChatClient routingChatClient(OpenAiChatModel fastChatModel) {
        return ChatClient.builder(fastChatModel).build();
    }

    /**
     * 规划/翻译专用的「快模型」ChatModel。与主模型共用同一个 API Key 与 base-url，
     * 只是换成响应更快的小模型 + 更小的输出上限 + 更低的温度。
     */
    @Bean
    public OpenAiChatModel fastChatModel() {
        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(trimTrailingSlash(baseUrl))
                .apiKey(apiKey)
                .completionsPath("/chat/completions")
                .build();

        OpenAiChatOptions chatOptions = OpenAiChatOptions.builder()
                .model(fastModel)
                .temperature(fastTemperature)
                .maxTokens(fastMaxTokens)
                .build();

        return OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(chatOptions)
                .build();
    }

    /**
     * 学习路线编排用的 ChatClient（无记忆、低温、快模型）。
     * {@code RoadmapComposeService} 优先注入它；调用失败时业务侧会自动退回模板兜底。
     */
    @Bean
    public ChatClient plannerChatClient(OpenAiChatModel fastChatModel) {
        return ChatClient.builder(fastChatModel).build();
    }

    @Bean
    @Primary
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
