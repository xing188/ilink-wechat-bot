package com.ilink.test.ilinkcheck.application.agent.spi.mcp;

import com.ilink.test.ilinkcheck.application.agent.dto.ParsedArticle;

public interface MediaParser {

    ParsedArticle parse(String url);
}
