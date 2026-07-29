package com.ilink.test.ilinkcheck.infrastructure.rag;

import com.ilink.test.ilinkcheck.application.agent.dto.review.ReviewSuggestion;
import com.ilink.test.ilinkcheck.application.agent.spi.rag.StyleGuideRetriever;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class StyleGuideRetrieverImpl implements StyleGuideRetriever {

    private final VectorStoreService vectorStoreService;

    public StyleGuideRetrieverImpl(VectorStoreService vectorStoreService) {
        this.vectorStoreService = vectorStoreService;
    }

    @Override
    public List<ReviewSuggestion> check(String draftText) {
        List<Document> relevantDocs = vectorStoreService.search(draftText, 5);

        return relevantDocs.stream()
                .map(doc -> new ReviewSuggestion(
                        doc.getMetadata().getOrDefault("category", "general").toString(),
                        doc.getMetadata().getOrDefault("location", "").toString(),
                        doc.getFormattedContent(),
                        doc.getMetadata().getOrDefault("example", "").toString()
                ))
                .toList();
    }
}
