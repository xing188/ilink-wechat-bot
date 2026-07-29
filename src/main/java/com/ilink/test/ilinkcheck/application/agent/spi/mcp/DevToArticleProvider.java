package com.ilink.test.ilinkcheck.application.agent.spi.mcp;

import com.ilink.test.ilinkcheck.application.agent.dto.roadmap.DevToArticle;

import java.util.List;

public interface DevToArticleProvider {

    List<DevToArticle> fetchByTag(String tag, int limit);
}
