package com.ilink.test.ilinkcheck.application.rag.model;

import java.util.Map;

/**
 * 通用RAG检索请求对象
 * 统一封装检索入参：目标知识库、查询文本、返回条数、扩展过滤属性
 * 作用：定义标准请求协议，所有RagRetriever实现接收相同入参，便于后续扩展过滤条件，不用频繁修改接口签名
 */
public record RagQuery(
        String knowledgeBaseId,    // 知识库唯一标识，用于RagService路由分发
        String queryText,          // 用户原始查询文本，用于向量检索
        int topK,                  // 需要召回的分片数量
        Map<String, Object> attributes // 自定义扩展参数，可用于元数据过滤、业务筛选
) {

    /**
     * 构造器校验与参数归一化
     */
    public RagQuery {
        if (knowledgeBaseId == null || knowledgeBaseId.isBlank()) {
            throw new IllegalArgumentException("knowledgeBaseId must not be blank");
        }
        if (queryText == null || queryText.isBlank()) {
            throw new IllegalArgumentException("queryText must not be blank");
        }
        // topK非法时兜底默认值5
        topK = topK > 0 ? topK : 5;
        // 不可变空集合，防止外部修改
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    /**
     * 快速构建：使用默认topK=5，无扩展参数
     */
    public static RagQuery of(RagKnowledgeBase knowledgeBase, String queryText) {
        return new RagQuery(knowledgeBase.id(), queryText, 5, Map.of());
    }

    /**
     * 快速构建：自定义topK，无扩展参数
     */
    public static RagQuery of(RagKnowledgeBase knowledgeBase, String queryText, int topK) {
        return new RagQuery(knowledgeBase.id(), queryText, topK, Map.of());
    }
}