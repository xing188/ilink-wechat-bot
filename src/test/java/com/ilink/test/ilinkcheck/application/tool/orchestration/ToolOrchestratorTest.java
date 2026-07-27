package com.ilink.test.ilinkcheck.application.tool.orchestration;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ToolOrchestratorTest {

    @Test
    void shouldExecuteReadOnlyParallelBatchConcurrently() {
        ToolExecutionPlanner planner = mock(ToolExecutionPlanner.class);
        ToolMethodRegistry registry = mock(ToolMethodRegistry.class);
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec responseSpec = mock(ChatClient.CallResponseSpec.class);
        ChatClient.AdvisorSpec advisorSpec = mock(ChatClient.AdvisorSpec.class);
        ToolCallback weatherCallback = mock(ToolCallback.class);
        ToolCallback newsCallback = mock(ToolCallback.class);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        AtomicInteger running = new AtomicInteger();
        AtomicInteger maxRunning = new AtomicInteger();
        Set<String> threads = java.util.concurrent.ConcurrentHashMap.newKeySet();

        try {
            when(planner.plan(anyString(), anyList(), anyList()))
                    .thenReturn(new ToolExecutionPlan(
                            List.of(new ToolExecutionBatch(
                                    ExecutionMode.PARALLEL,
                                    List.of(
                                            new ToolExecutionStep("queryWeather", Map.of("city", "北京"), "查天气"),
                                            new ToolExecutionStep("queryNews", Map.of("category", "tech"), "查科技新闻")
                                    ),
                                    "independent read-only lookups"
                            )),
                            "parallel-plan"
                    ))
                    .thenReturn(new ToolExecutionPlan(List.of(), "done-or-no-plan"));

            when(registry.findByCallbackName("queryWeather")).thenReturn(new ToolMethodDefinition(
                    "weather", "queryWeather", weatherCallback, true, "weather", "{}"
            ));
            when(registry.findByCallbackName("queryNews")).thenReturn(new ToolMethodDefinition(
                    "news", "queryNews", newsCallback, true, "news", "{}"
            ));

            doAnswer(invocation -> {
                int current = running.incrementAndGet();
                maxRunning.updateAndGet(previous -> Math.max(previous, current));
                threads.add(Thread.currentThread().getName());
                Thread.sleep(120);
                running.decrementAndGet();
                return "weather-result";
            }).when(weatherCallback).call(anyString());

            doAnswer(invocation -> {
                int current = running.incrementAndGet();
                maxRunning.updateAndGet(previous -> Math.max(previous, current));
                threads.add(Thread.currentThread().getName());
                Thread.sleep(120);
                running.decrementAndGet();
                return "news-result";
            }).when(newsCallback).call(anyString());

            prepareSummaryClient(chatClient, requestSpec, responseSpec, advisorSpec, "summary reply");

            ToolOrchestrator orchestrator = new ToolOrchestrator(planner, registry, chatClient, executor);

            ToolOrchestrationResult result = orchestrator.orchestrate(
                    "user-1",
                    "北京天气和科技新闻",
                    List.of("weather", "news"),
                    "system"
            );

            assertThat(result).isNotNull();
            assertThat(result.usedParallelExecution()).isTrue();
            assertThat(result.finalReply()).isEqualTo("summary reply");
            assertThat(maxRunning.get()).isGreaterThan(1);
            assertThat(threads.size()).isGreaterThan(1);
            verify(advisorSpec).param(ChatMemory.CONVERSATION_ID, "user-1");
            verify(weatherCallback).call(anyString());
            verify(newsCallback).call(anyString());
        }
        finally {
            executor.shutdownNow();
        }
    }

    @Test
    void shouldDowngradeParallelBatchToSerialWhenAnyStepIsNotReadOnly() {
        ToolExecutionPlanner planner = mock(ToolExecutionPlanner.class);
        ToolMethodRegistry registry = mock(ToolMethodRegistry.class);
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec responseSpec = mock(ChatClient.CallResponseSpec.class);
        ChatClient.AdvisorSpec advisorSpec = mock(ChatClient.AdvisorSpec.class);
        ToolCallback reminderCallback = mock(ToolCallback.class);
        ToolCallback weatherCallback = mock(ToolCallback.class);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        AtomicInteger running = new AtomicInteger();
        AtomicInteger maxRunning = new AtomicInteger();

        try {
            when(planner.plan(anyString(), anyList(), anyList()))
                    .thenReturn(new ToolExecutionPlan(
                            List.of(new ToolExecutionBatch(
                                    ExecutionMode.PARALLEL,
                                    List.of(
                                            new ToolExecutionStep("createReminder", Map.of("request", "十分钟后提醒我开会"), "创建提醒"),
                                            new ToolExecutionStep("queryWeather", Map.of("city", "北京"), "查天气")
                                    ),
                                    "requested parallel but contains side effects"
                            )),
                            "mixed-plan"
                    ))
                    .thenReturn(new ToolExecutionPlan(List.of(), "done-or-no-plan"));

            when(registry.findByCallbackName("createReminder")).thenReturn(new ToolMethodDefinition(
                    "reminder", "createReminder", reminderCallback, false, "create reminder", "{}"
            ));
            when(registry.findByCallbackName("queryWeather")).thenReturn(new ToolMethodDefinition(
                    "weather", "queryWeather", weatherCallback, true, "weather", "{}"
            ));

            doAnswer(invocation -> {
                int current = running.incrementAndGet();
                maxRunning.updateAndGet(previous -> Math.max(previous, current));
                Thread.sleep(80);
                running.decrementAndGet();
                return "reminder-created";
            }).when(reminderCallback).call(anyString());

            doAnswer(invocation -> {
                int current = running.incrementAndGet();
                maxRunning.updateAndGet(previous -> Math.max(previous, current));
                Thread.sleep(80);
                running.decrementAndGet();
                return "weather-result";
            }).when(weatherCallback).call(anyString());

            prepareSummaryClient(chatClient, requestSpec, responseSpec, advisorSpec, "summary reply");

            ToolOrchestrator orchestrator = new ToolOrchestrator(planner, registry, chatClient, executor);

            ToolOrchestrationResult result = orchestrator.orchestrate(
                    "user-2",
                    "先提醒我开会再查北京天气",
                    List.of("reminder", "weather"),
                    "system"
            );

            assertThat(result).isNotNull();
            assertThat(result.usedParallelExecution()).isFalse();
            assertThat(maxRunning.get()).isEqualTo(1);
        }
        finally {
            executor.shutdownNow();
        }
    }

    @Test
    void shouldSupportConditionalSerialWorkflowAcrossPlanningRounds() {
        ToolExecutionPlanner planner = mock(ToolExecutionPlanner.class);
        ToolMethodRegistry registry = mock(ToolMethodRegistry.class);
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec responseSpec = mock(ChatClient.CallResponseSpec.class);
        ChatClient.AdvisorSpec advisorSpec = mock(ChatClient.AdvisorSpec.class);
        ToolCallback weatherCallback = mock(ToolCallback.class);
        ToolCallback reminderCallback = mock(ToolCallback.class);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            when(planner.plan(anyString(), anyList(), anyList()))
                    .thenReturn(new ToolExecutionPlan(
                            List.of(new ToolExecutionBatch(
                                    ExecutionMode.SERIAL,
                                    List.of(new ToolExecutionStep("queryWeather", Map.of("city", "杭州", "includeForecast", true), "先查天气")),
                                    "first-round-weather"
                            )),
                            "weather-first"
                    ))
                    .thenReturn(new ToolExecutionPlan(
                            List.of(new ToolExecutionBatch(
                                    ExecutionMode.SERIAL,
                                    List.of(new ToolExecutionStep("createReminder", Map.of("request", "明天出门前提醒我带伞"), "根据天气结果创建提醒")),
                                    "second-round-reminder"
                            )),
                            "reminder-second"
                    ))
                    .thenReturn(new ToolExecutionPlan(List.of(), "done-or-no-plan"));

            when(registry.findByCallbackName("queryWeather")).thenReturn(new ToolMethodDefinition(
                    "weather", "queryWeather", weatherCallback, true, "weather", "{}"
            ));
            when(registry.findByCallbackName("createReminder")).thenReturn(new ToolMethodDefinition(
                    "reminder", "createReminder", reminderCallback, false, "reminder", "{}"
            ));

            when(weatherCallback.call(anyString())).thenReturn("杭州明天有雨");
            when(reminderCallback.call(anyString())).thenReturn("已创建提醒：明天出门前提醒我带伞");

            prepareSummaryClient(chatClient, requestSpec, responseSpec, advisorSpec, "summary reply");

            ToolOrchestrator orchestrator = new ToolOrchestrator(planner, registry, chatClient, executor);

            ToolOrchestrationResult result = orchestrator.orchestrate(
                    "user-3",
                    "先查杭州明天天气，晴天提醒A，否则提醒B",
                    List.of("weather", "reminder"),
                    "system"
            );

            assertThat(result).isNotNull();
            assertThat(result.usedParallelExecution()).isFalse();
            assertThat(result.outcomes()).hasSize(2);
            assertThat(result.outcomes().get(0).step().toolName()).isEqualTo("queryWeather");
            assertThat(result.outcomes().get(1).step().toolName()).isEqualTo("createReminder");
            verify(weatherCallback, times(1)).call(anyString());
            verify(reminderCallback, times(1)).call(anyString());
        }
        finally {
            executor.shutdownNow();
        }
    }

    private void prepareSummaryClient(ChatClient chatClient,
                                      ChatClient.ChatClientRequestSpec requestSpec,
                                      ChatClient.CallResponseSpec responseSpec,
                                      ChatClient.AdvisorSpec advisorSpec,
                                      String reply) {
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
        when(responseSpec.content()).thenReturn(reply);
    }
}
