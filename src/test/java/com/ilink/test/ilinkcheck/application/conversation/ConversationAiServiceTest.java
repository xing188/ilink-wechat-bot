package com.ilink.test.ilinkcheck.application.conversation;

import com.ilink.test.ilinkcheck.application.tool.ToolRegistry;
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

class ConversationAiServiceTest {

    @Test
    void shouldPassConversationIdToChatMemoryAdvisor() {
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec responseSpec = mock(ChatClient.CallResponseSpec.class);
        ChatClient.AdvisorSpec advisorSpec = mock(ChatClient.AdvisorSpec.class);
        ToolRegistry toolRegistry = mock(ToolRegistry.class);
        Object[] toolObjects = {new Object()};

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(advisorSpec.param(anyString(), any())).thenReturn(advisorSpec);
        doAnswer(invocation -> {
            Consumer<ChatClient.AdvisorSpec> consumer = invocation.getArgument(0);
            consumer.accept(advisorSpec);
            return requestSpec;
        }).when(requestSpec).advisors(org.mockito.ArgumentMatchers.<Consumer<ChatClient.AdvisorSpec>>any());
        when(toolRegistry.toolObjects()).thenReturn(toolObjects);
        when(requestSpec.tools(toolObjects)).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn("plain reply");

        ConversationAiService service = new ConversationAiService(chatClient, "base prompt", toolRegistry);

        String result = service.generateReply("user-42", "hello");

        assertThat(result).isEqualTo("plain reply");
        verify(advisorSpec).param(ChatMemory.CONVERSATION_ID, "user-42");
        verify(requestSpec).tools(toolObjects);
        verify(requestSpec).user("hello");
    }
}
