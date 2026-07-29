package com.ilink.test.ilinkcheck.application.agent.spi.mcp;

import com.ilink.test.ilinkcheck.application.agent.dto.roadmap.SearchResult;

import java.util.List;

public interface WebSearchProvider {

    List<SearchResult> search(String query, int limit);
}
