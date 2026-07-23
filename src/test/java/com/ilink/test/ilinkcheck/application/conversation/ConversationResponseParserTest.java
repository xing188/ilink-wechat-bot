package com.ilink.test.ilinkcheck.application.conversation;

import com.ilink.test.ilinkcheck.model.ConversationResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationResponseParserTest {

    private final ConversationResponseParser parser = new ConversationResponseParser();

    @Test
    void shouldParseImageIntentReply() {
        ConversationResult result = parser.parse("user-42", "[DRAW_IMAGE]:a running cat");

        assertThat(result.type()).isEqualTo(ConversationResult.Type.DRAW_IMAGE);
        assertThat(result.content()).isEqualTo("a running cat");
    }

    @Test
    void shouldFallbackWhenReplyIsBlank() {
        ConversationResult result = parser.parse("user-42", "   ");

        assertThat(result.type()).isEqualTo(ConversationResult.Type.TEXT);
        assertThat(result.content()).isEqualTo("我暂时没有生成可用回复，请稍后再试。");
    }
}
