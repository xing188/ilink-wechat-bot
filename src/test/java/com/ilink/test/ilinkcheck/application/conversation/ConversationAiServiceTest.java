package com.ilink.test.ilinkcheck.application.conversation;

import com.ilink.test.ilinkcheck.application.tool.ToolRegistry;
import com.ilink.test.ilinkcheck.application.tool.orchestration.ToolOrchestrator;
import com.ilink.test.ilinkcheck.application.tool.selection.ToolContextAssembler;
import com.ilink.test.ilinkcheck.application.tool.selection.ToolSelectionResult;
import com.ilink.test.ilinkcheck.application.tool.selection.ToolSelector;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;

import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConversationAiServiceTest {

    @Test
    void shouldPassConversationIdAndSelectedToolsToPrompt() {
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec responseSpec = mock(ChatClient.CallResponseSpec.class);
        ChatClient.AdvisorSpec advisorSpec = mock(ChatClient.AdvisorSpec.class);
        ToolRegistry toolRegistry = mock(ToolRegistry.class);
        ToolSelector toolSelector = mock(ToolSelector.class);
        ToolContextAssembler toolContextAssembler = mock(ToolContextAssembler.class);
        ToolOrchestrator toolOrchestrator = mock(ToolOrchestrator.class);
        Object[] toolObjects = {new Object()};

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(advisorSpec.param(anyString(), any())).thenReturn(advisorSpec);
        doAnswer(invocation -> {
            Consumer<ChatClient.AdvisorSpec> consumer = invocation.getArgument(0);
            consumer.accept(advisorSpec);
            return requestSpec;
        }).when(requestSpec).advisors(org.mockito.ArgumentMatchers.<Consumer<ChatClient.AdvisorSpec>>any());
        when(toolSelector.select("user-42", "hello")).thenReturn(ToolSelectionResult.selected(List.of("news"), "rule"));
        when(toolRegistry.toolObjects(List.of("news"))).thenReturn(toolObjects);
        when(toolContextAssembler.buildPromptSection(List.of("news"))).thenReturn("news prompt");
        when(toolOrchestrator.orchestrate(anyString(), anyString(), any(), anyString())).thenReturn(null);
        when(requestSpec.tools(toolObjects)).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn("plain reply");

        ConversationAiService service = new ConversationAiService(
                chatClient,
                "base prompt",
                toolRegistry,
                toolSelector,
                toolContextAssembler,
                toolOrchestrator
        );

        String result = service.generateReply("user-42", "hello");

        assertThat(result).isEqualTo("plain reply");
        verify(advisorSpec).param(ChatMemory.CONVERSATION_ID, "user-42");
        verify(toolSelector).select("user-42", "hello");
        verify(requestSpec).tools(toolObjects);
        verify(requestSpec).user("hello");
    }

    @Test
    void shouldSkipToolBindingWhenSelectorReturnsNoTools() {
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec responseSpec = mock(ChatClient.CallResponseSpec.class);
        ChatClient.AdvisorSpec advisorSpec = mock(ChatClient.AdvisorSpec.class);
        ToolRegistry toolRegistry = mock(ToolRegistry.class);
        ToolSelector toolSelector = mock(ToolSelector.class);
        ToolContextAssembler toolContextAssembler = mock(ToolContextAssembler.class);
        ToolOrchestrator toolOrchestrator = mock(ToolOrchestrator.class);

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(advisorSpec.param(anyString(), any())).thenReturn(advisorSpec);
        doAnswer(invocation -> {
            Consumer<ChatClient.AdvisorSpec> consumer = invocation.getArgument(0);
            consumer.accept(advisorSpec);
            return requestSpec;
        }).when(requestSpec).advisors(org.mockito.ArgumentMatchers.<Consumer<ChatClient.AdvisorSpec>>any());
        when(toolSelector.select("user-42", "chat only")).thenReturn(ToolSelectionResult.noTools("llm-none"));
        when(toolRegistry.toolObjects(List.of())).thenReturn(new Object[0]);
        when(toolContextAssembler.buildPromptSection(List.of())).thenReturn("no tools");
        when(toolOrchestrator.orchestrate(anyString(), anyString(), any(), anyString())).thenReturn(null);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn("plain reply");

        ConversationAiService service = new ConversationAiService(
                chatClient,
                "base prompt",
                toolRegistry,
                toolSelector,
                toolContextAssembler,
                toolOrchestrator
        );

        String result = service.generateReply("user-42", "chat only");

        assertThat(result).isEqualTo("plain reply");
        verify(requestSpec, never()).tools(any(Object[].class));
    }
}
