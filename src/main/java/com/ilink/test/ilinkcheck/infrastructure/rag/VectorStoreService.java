package com.ilink.test.ilinkcheck.infrastructure.rag;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 向量库门面服务。
 * 业务层不直接依赖 Spring AI 的 VectorStore，统一通过这里做入库和检索。
 */
@Service
public class VectorStoreService {

    private final VectorStore vectorStore;

    public VectorStoreService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    /**
     * 批量写入文档到向量库。
     */
    public void addDocuments(List<Document> documents) {
        vectorStore.add(documents);
    }

    /**
     * 基于 query 做相似度检索。
     * 这里显式使用 SearchRequest，把 topK 真正传给底层向量库。
     */
    public List<Document> search(String query, int topK) {
        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(Math.max(1, topK))
                .build();
        return vectorStore.similaritySearch(request);
    }

    public List<Document> search(String query) {
        return search(query, 5);
    }
}
