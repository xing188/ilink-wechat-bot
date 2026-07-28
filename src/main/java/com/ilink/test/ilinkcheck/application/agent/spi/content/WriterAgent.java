package com.ilink.test.ilinkcheck.application.agent.spi.content;

import com.ilink.test.ilinkcheck.application.agent.dto.WritingDraft;

public interface WriterAgent {

    WritingDraft write(String topic, String platform, String style);
}
