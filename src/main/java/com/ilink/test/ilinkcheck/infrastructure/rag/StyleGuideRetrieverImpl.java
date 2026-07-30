package com.ilink.test.ilinkcheck.infrastructure.rag;

import com.ilink.test.ilinkcheck.application.agent.dto.review.ReviewSuggestion;
import com.ilink.test.ilinkcheck.application.agent.spi.rag.StyleGuideRetriever;
import com.ilink.test.ilinkcheck.application.rag.RagService;
import com.ilink.test.ilinkcheck.application.rag.model.RagChunk;
import com.ilink.test.ilinkcheck.application.rag.model.RagKnowledgeBase;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 审稿场景兼容适配器。
 * 对外继续暴露旧的 StyleGuideRetriever 接口，对内改走通用 RAG 服务。
 */
@Service
public class StyleGuideRetrieverImpl implements StyleGuideRetriever {

    private final RagService ragService;

    public StyleGuideRetrieverImpl(RagService ragService) {
        this.ragService = ragService;
    }

    @Override
    public List<ReviewSuggestion> check(String draftText) {
        List<RagChunk> chunks = ragService.retrieve(RagKnowledgeBase.STYLE_GUIDE, draftText, 5);
        return chunks.stream()
                .map(chunk -> new ReviewSuggestion(
                        chunk.metadata().getOrDefault("category", "general").toString(),
                        chunk.metadata().getOrDefault("location", "").toString(),
                        chunk.content(),
                        chunk.metadata().getOrDefault("example", "").toString()
                ))
                .toList();
    }
}
