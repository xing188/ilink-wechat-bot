package com.ilink.test.ilinkcheck.application.rag.model;

import java.util.Map;

/**
 * 通用检索请求。
 * 这里先把 query、topK、知识域、额外过滤条件都统一起来，后面接不同 retriever 时不用改协议。
 */
public record RagQuery(
        String knowledgeBaseId,
        String queryText,
        int topK,
        Map<String, Object> attributes
) {

    public RagQuery {
        if (knowledgeBaseId == null || knowledgeBaseId.isBlank()) {
            throw new IllegalArgumentException("knowledgeBaseId must not be blank");
        }
        if (queryText == null || queryText.isBlank()) {
            throw new IllegalArgumentException("queryText must not be blank");
        }
        topK = topK > 0 ? topK : 5;
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    public static RagQuery of(RagKnowledgeBase knowledgeBase, String queryText) {
        return new RagQuery(knowledgeBase.id(), queryText, 5, Map.of());
    }

    public static RagQuery of(RagKnowledgeBase knowledgeBase, String queryText, int topK) {
        return new RagQuery(knowledgeBase.id(), queryText, topK, Map.of());
    }
}
