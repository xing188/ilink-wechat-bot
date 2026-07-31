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

/**
 * RAG向量库配置类
 * 适配阿里云DashScope（通义千问）Embedding接口
 * 关键点：DashScope兼容OpenAI协议，所以直接复用SpringAI OpenAi系列客户端
 * 向量存储：SimpleVectorStore【内存向量库】
 */
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

    //文本向量化能力
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
