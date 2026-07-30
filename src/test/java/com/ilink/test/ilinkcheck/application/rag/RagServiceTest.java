package com.ilink.test.ilinkcheck.application.rag;

import com.ilink.test.ilinkcheck.application.rag.model.RagChunk;
import com.ilink.test.ilinkcheck.application.rag.model.RagKnowledgeBase;
import com.ilink.test.ilinkcheck.application.rag.model.RagQuery;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RagServiceTest {

    @Test
    void shouldRouteQueryToMatchedRetriever() {
        RagRetriever retriever = new RagRetriever() {
            @Override
            public String knowledgeBaseId() {
                return RagKnowledgeBase.STYLE_GUIDE.id();
            }

            @Override
            public List<RagChunk> retrieve(RagQuery query) {
                return List.of(new RagChunk("rule-1", "Redis 延迟要加空格", Map.of("category", "spacing")));
            }
        };

        RagService ragService = new RagService(List.of(retriever));

        List<RagChunk> chunks = ragService.retrieve(RagKnowledgeBase.STYLE_GUIDE, "Redis延迟", 3);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.getFirst().content()).isEqualTo("Redis 延迟要加空格");
    }

    @Test
    void shouldFailFastWhenKnowledgeBaseHasNoRetriever() {
        RagService ragService = new RagService(List.of());

        assertThatThrownBy(() -> ragService.retrieve(RagKnowledgeBase.STYLE_GUIDE, "Redis延迟", 3))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("style-guide");
    }
}
