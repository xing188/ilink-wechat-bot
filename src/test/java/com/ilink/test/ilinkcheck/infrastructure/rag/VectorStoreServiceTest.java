package com.ilink.test.ilinkcheck.infrastructure.rag;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VectorStoreServiceTest {

    @Test
    void shouldPassTopKIntoSearchRequest() {
        VectorStore vectorStore = mock(VectorStore.class);
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(new Document("Redis 延迟")));

        VectorStoreService service = new VectorStoreService(vectorStore);

        List<Document> documents = service.search("redis延迟", 3);

        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(captor.capture());
        SearchRequest request = captor.getValue();

        assertThat(documents).hasSize(1);
        assertThat(request.getQuery()).isEqualTo("redis延迟");
        assertThat(request.getTopK()).isEqualTo(3);
    }
}
