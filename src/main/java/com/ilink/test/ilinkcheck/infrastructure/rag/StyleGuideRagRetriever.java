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

    private final VectorStoreService vectorStoreService;

    public StyleGuideRagRetriever(VectorStoreService vectorStoreService) {
        this.vectorStoreService = vectorStoreService;
    }

    @Override
    public String knowledgeBaseId() {
        return RagKnowledgeBase.STYLE_GUIDE.id();
    }

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
