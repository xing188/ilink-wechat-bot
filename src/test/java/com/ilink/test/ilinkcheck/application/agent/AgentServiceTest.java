package com.ilink.test.ilinkcheck.application.agent;

import com.ilink.test.ilinkcheck.application.tool.ToolRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AgentServiceTest {

    @Test
    void shouldCallChatClientAndReturnResult() {
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec responseSpec = mock(ChatClient.CallResponseSpec.class);
        ToolRegistry toolRegistry = mock(ToolRegistry.class);

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.tools(any())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn("现在时间是 2026年7月27日 14:30。");

        AgentService agentService = new AgentService(chatClient, toolRegistry);
        String result = agentService.execute("现在几点了");

        assertThat(result).isEqualTo("现在时间是 2026年7月27日 14:30。");
        verify(requestSpec).system(anyString());
        verify(requestSpec).user("现在几点了");
        verify(requestSpec).tools(any());
    }
}
