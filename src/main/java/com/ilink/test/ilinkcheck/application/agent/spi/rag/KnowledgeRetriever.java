package com.ilink.test.ilinkcheck.application.agent.spi.rag;

import com.ilink.test.ilinkcheck.application.agent.dto.KnowledgeItem;

import java.util.List;

public interface KnowledgeRetriever {

    List<KnowledgeItem> search(String query);

    List<KnowledgeItem> searchByCategory(String category);
}
