package com.ilink.test.ilinkcheck.infrastructure.rag;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class VectorStoreService {

    private final VectorStore vectorStore;

    public VectorStoreService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public void addDocuments(List<Document> documents) {
        vectorStore.add(documents);
    }

    public List<Document> search(String query, int topK) {
        return vectorStore.similaritySearch(query);
    }

    public List<Document> search(String query) {
        return search(query, 5);
    }
}
