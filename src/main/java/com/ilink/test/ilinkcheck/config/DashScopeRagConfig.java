package com.ilink.test.ilinkcheck.config;

import com.ilink.test.ilinkcheck.infrastructure.rag.VectorStoreService;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DashScopeRagConfig {

    @Value("${spring.ai.openai.api-key}")
    private String apiKey;

    @Value("${spring.ai.openai.base-url}")
    private String baseUrl;

    @Value("${spring.ai.openai.embedding.model:text-embedding-v3}")
    private String embeddingModel;

    @Bean
    public OpenAiApi dashScopeEmbeddingApi() {
        return OpenAiApi.builder()
                .baseUrl(trimTrailingSlash(baseUrl))
                .apiKey(apiKey)
                .build();
    }

    @Bean
    public EmbeddingModel dashScopeEmbeddingModel(OpenAiApi dashScopeEmbeddingApi) {
        OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                .model(embeddingModel)
                .build();
        return new OpenAiEmbeddingModel(dashScopeEmbeddingApi, MetadataMode.ALL, options);
    }

    @Bean
    public VectorStore simpleVectorStore(EmbeddingModel dashScopeEmbeddingModel) {
        return SimpleVectorStore.builder(dashScopeEmbeddingModel).build();
    }

    @Bean
    public VectorStoreService vectorStoreService(VectorStore simpleVectorStore) {
        return new VectorStoreService(simpleVectorStore);
    }

    private String trimTrailingSlash(String value) {
        if (value == null) return null;
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
