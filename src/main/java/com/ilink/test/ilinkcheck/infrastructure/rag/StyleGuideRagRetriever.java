package com.ilink.test.ilinkcheck.infrastructure.rag;

import com.ilink.test.ilinkcheck.application.rag.RagRetriever;
import com.ilink.test.ilinkcheck.application.rag.model.RagChunk;
import com.ilink.test.ilinkcheck.application.rag.model.RagKnowledgeBase;
import com.ilink.test.ilinkcheck.application.rag.model.RagQuery;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 写作规范域的通用 RAG 检索器。
 * 这一层只负责把向量检索结果转换成通用 RagChunk，不关心上层是审稿还是别的功能。
 */
@Service
public class StyleGuideRagRetriever implements RagRetriever {

    // 底层向量门面，和你之前整套向量存储能力打通
    private final VectorStoreService vectorStoreService;

    public StyleGuideRagRetriever(VectorStoreService vectorStoreService) {
        this.vectorStoreService = vectorStoreService;
    }

    /**
     * 绑定当前检索器归属的知识库ID
     * RagService启动时依靠这个ID建立路由映射
     */
    @Override
    public String knowledgeBaseId() {
        return RagKnowledgeBase.STYLE_GUIDE.id();
    }

    /**
     * 通用检索标准方法，框架统一调用入口
     * @param query 通用检索请求：查询文本、topK、扩展参数
     * @return 统一分片模型 List<RagChunk>
     */
    @Override
    public List<RagChunk> retrieve(RagQuery query) {
        List<Document> relevantDocs = vectorStoreService.search(query.queryText(), query.topK());
        return relevantDocs.stream()
                .map(this::toChunk)
                .toList();
    }

    private RagChunk toChunk(Document document) {
        return new RagChunk(
                document.getId(),
                document.getFormattedContent(),
                document.getMetadata()
        );
    }
}
