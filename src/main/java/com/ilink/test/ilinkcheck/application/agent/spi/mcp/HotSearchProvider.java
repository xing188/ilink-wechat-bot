package com.ilink.test.ilinkcheck.application.agent.spi.mcp;

import com.ilink.test.ilinkcheck.application.agent.dto.HotSearchResult;

import java.util.List;

public interface HotSearchProvider {

    List<HotSearchResult> fetchHotSearch(String source);

    List<HotSearchResult> searchByKeyword(String keyword);
}
