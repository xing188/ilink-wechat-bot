package com.ilink.test.ilinkcheck.application.agent.spi.content;

import com.ilink.test.ilinkcheck.application.agent.dto.FactCheckReport;

public interface FactChecker {

    FactCheckReport verify(String claim);
}
