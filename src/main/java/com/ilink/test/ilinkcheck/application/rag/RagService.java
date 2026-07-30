package com.ilink.test.ilinkcheck.application.rag;

import com.ilink.test.ilinkcheck.application.rag.model.RagChunk;
import com.ilink.test.ilinkcheck.application.rag.model.RagKnowledgeBase;
import com.ilink.test.ilinkcheck.application.rag.model.RagQuery;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 通用 RAG 调度器。
 * 只负责“按知识域选择 retriever”，不关心底层是向量库、关键词搜索还是别的实现。
 */
@Service
public class RagService {

    private final Map<String, RagRetriever> retrieversByKnowledgeBaseId;

    public RagService(List<RagRetriever> retrievers) {
        this.retrieversByKnowledgeBaseId = retrievers.stream()
                .collect(Collectors.toMap(RagRetriever::knowledgeBaseId, retriever -> retriever, (left, right) -> left));
    }

    public Optional<RagRetriever> findRetriever(String knowledgeBaseId) {
        if (!StringUtils.hasText(knowledgeBaseId)) {
            return Optional.empty();
        }
        return Optional.ofNullable(retrieversByKnowledgeBaseId.get(knowledgeBaseId.trim()));
    }

    public List<RagChunk> retrieve(RagQuery query) {
        RagRetriever retriever = findRetriever(query.knowledgeBaseId())
                .orElseThrow(() -> new IllegalStateException("No rag retriever found for knowledgeBaseId=" + query.knowledgeBaseId()));
        return retriever.retrieve(query);
    }

    public List<RagChunk> retrieve(RagKnowledgeBase knowledgeBase, String queryText, int topK) {
        return retrieve(new RagQuery(knowledgeBase.id(), queryText, topK, Map.of()));
    }
}
