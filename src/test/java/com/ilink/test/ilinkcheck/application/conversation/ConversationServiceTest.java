package com.ilink.test.ilinkcheck.application.conversation;

import com.ilink.test.ilinkcheck.model.ConversationResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConversationServiceTest {

    @Test
    void shouldDelegateToAiServiceAndParser() {
        ConversationAiService conversationAiService = mock(ConversationAiService.class);
        ConversationResponseParser conversationResponseParser = mock(ConversationResponseParser.class);
        when(conversationAiService.generateReply("user-42", "hello")).thenReturn("plain reply");
        when(conversationResponseParser.parse("user-42", "plain reply"))
                .thenReturn(ConversationResult.text("plain reply"));

        ConversationService service = new ConversationService(conversationAiService, conversationResponseParser);

        ConversationResult result = service.handleTextMessage("user-42", "hello");

        assertThat(result.type()).isEqualTo(ConversationResult.Type.TEXT);
        assertThat(result.content()).isEqualTo("plain reply");
        verify(conversationAiService).generateReply("user-42", "hello");
        verify(conversationResponseParser).parse("user-42", "plain reply");
    }
}
