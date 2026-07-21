package com.ilink.test.ilinkcheck.service;

import com.ilink.test.ilinkcheck.model.ConversationResult;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;

import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConversationServiceTest {

    @Test
    void shouldPassConversationIdToChatMemoryAdvisor() {
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec responseSpec = mock(ChatClient.CallResponseSpec.class);
        ChatClient.AdvisorSpec advisorSpec = mock(ChatClient.AdvisorSpec.class);

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(advisorSpec.param(anyString(), any())).thenReturn(advisorSpec);
        doAnswer(invocation -> {
            Consumer<ChatClient.AdvisorSpec> consumer = invocation.getArgument(0);
            consumer.accept(advisorSpec);
            return requestSpec;
        }).when(requestSpec).advisors(org.mockito.ArgumentMatchers.<Consumer<ChatClient.AdvisorSpec>>any());
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn("plain reply");

        ConversationService service = new ConversationService(chatClient, "base prompt");

        ConversationResult result = service.handleTextMessage("user-42", "hello");

        assertThat(result.type()).isEqualTo(ConversationResult.Type.TEXT);
        assertThat(result.content()).isEqualTo("plain reply");
        verify(advisorSpec).param(ChatMemory.CONVERSATION_ID, "user-42");
        verify(requestSpec).user("hello");
    }
}
