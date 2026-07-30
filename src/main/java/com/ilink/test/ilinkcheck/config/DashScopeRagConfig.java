package com.ilink.test.ilinkcheck.config;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.embedding.DashScopeEmbeddingModel;
import com.alibaba.cloud.ai.dashscope.embedding.DashScopeEmbeddingOptions;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DashScopeRagConfig {

    @Value("${spring.ai.dashscope.api-key}")
    private String apiKey;

    @Bean
    public DashScopeApi dashScopeApi() {
        return DashScopeApi.builder()
                .apiKey(apiKey)
                .baseUrl("https://dashscope.aliyuncs.com")
                .build();
    }

    @Bean
    public DashScopeEmbeddingModel dashScopeEmbeddingModel(DashScopeApi dashScopeApi) {
        DashScopeEmbeddingOptions options = DashScopeEmbeddingOptions.builder()
                .model("text-embedding-v3")
                .build();
        return new DashScopeEmbeddingModel(dashScopeApi, MetadataMode.EMBED, options);
    }

    @Bean
    public VectorStore vectorStore(EmbeddingModel embeddingModel) {
        return SimpleVectorStore.builder(embeddingModel).build();
    }
}
