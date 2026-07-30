package com.ilink.test.ilinkcheck.infrastructure.rag;

import com.ilink.test.ilinkcheck.application.agent.dto.review.ReviewSuggestion;
import com.ilink.test.ilinkcheck.application.rag.RagService;
import com.ilink.test.ilinkcheck.application.rag.model.RagChunk;
import com.ilink.test.ilinkcheck.application.rag.model.RagKnowledgeBase;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StyleGuideRetrieverImplTest {

    @Test
    void shouldMapGenericRagChunkToReviewSuggestion() {
        RagService ragService = mock(RagService.class);
        when(ragService.retrieve(eq(RagKnowledgeBase.STYLE_GUIDE), eq("redis延迟"), eq(5)))
                .thenReturn(List.of(new RagChunk(
                        "rule-1",
                        "中文与英文之间需要加空格",
                        Map.of(
                                "category", "spacing",
                                "location", "中英文",
                                "example", "Redis 延迟"
                        )
                )));

        StyleGuideRetrieverImpl retriever = new StyleGuideRetrieverImpl(ragService);

        List<ReviewSuggestion> suggestions = retriever.check("redis延迟");

        assertThat(suggestions).hasSize(1);
        assertThat(suggestions.getFirst().type()).isEqualTo("spacing");
        assertThat(suggestions.getFirst().location()).isEqualTo("中英文");
        assertThat(suggestions.getFirst().message()).isEqualTo("中文与英文之间需要加空格");
        assertThat(suggestions.getFirst().suggestion()).isEqualTo("Redis 延迟");
        verify(ragService).retrieve(RagKnowledgeBase.STYLE_GUIDE, "redis延迟", 5);
    }
}
