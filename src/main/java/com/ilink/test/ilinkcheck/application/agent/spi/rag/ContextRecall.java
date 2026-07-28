package com.ilink.test.ilinkcheck.application.agent.spi.rag;

import com.ilink.test.ilinkcheck.application.agent.dto.ContextWindow;

public interface ContextRecall {

    ContextWindow recall(String conversationId);
}
