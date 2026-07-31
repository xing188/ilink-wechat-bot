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
 * 【应用层】通用 RAG 调度器
 * 核心职责：路由分发，根据知识库ID选择对应的检索实现
 * 设计思想：
 * 1. 上层业务只调用 RagService，不需要感知具体检索器实现
 * 2. 支持多知识库隔离，不同知识库可以挂载完全不同的检索实现
 * 3. 遵循开闭原则：新增知识库，只新增 RagRetriever 实现，调度器无需修改
 */
@Service
public class RagService {

    /**
     * key: 知识库唯一id
     * value: 当前知识库对应的检索器实现
     */
    private final Map<String, RagRetriever> retrieversByKnowledgeBaseId;

    /**
     * Spring自动注入所有 RagRetriever 的实现类，存入Map做路由
     * 冲突策略：同一个知识库ID多个实现，保留第一个加载的实现 (left,right)->left
     */
    public RagService(List<RagRetriever> retrievers) {
        this.retrieversByKnowledgeBaseId = retrievers.stream()
                .collect(Collectors.toMap(RagRetriever::knowledgeBaseId, retriever -> retriever, (left, right) -> left));
    }

    /**
     * 根据知识库ID查找对应的检索器
     * @param knowledgeBaseId 知识库标识
     * @return 存在则返回，空则 Optional.empty
     */
    public Optional<RagRetriever> findRetriever(String knowledgeBaseId) {
        if (!StringUtils.hasText(knowledgeBaseId)) {
            return Optional.empty();
        }
        return Optional.ofNullable(retrieversByKnowledgeBaseId.get(knowledgeBaseId.trim()));
    }

    /**
     * 标准检索入口
     * @param query 封装好的检索请求对象（知识库ID、查询文本、topK、扩展参数）
     * @return 检索片段列表 RagChunk
     */
    public List<RagChunk> retrieve(RagQuery query) {
        RagRetriever retriever = findRetriever(query.knowledgeBaseId())
                .orElseThrow(() -> new IllegalStateException("No rag retriever found for knowledgeBaseId=" + query.knowledgeBaseId()));
        return retriever.retrieve(query);
    }

    /**
     * 便捷重载方法：简化调用，外部不用手动构造RagQuery
     */
    public List<RagChunk> retrieve(RagKnowledgeBase knowledgeBase, String queryText, int topK) {
        return retrieve(new RagQuery(knowledgeBase.id(), queryText, topK, Map.of()));
    }
}
