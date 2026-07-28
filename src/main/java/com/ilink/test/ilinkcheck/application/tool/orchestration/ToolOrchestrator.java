package com.ilink.test.ilinkcheck.application.tool.orchestration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Service
@Slf4j
public class ToolOrchestrator {

    private static final int MAX_PLANNING_ROUNDS = 4;

    private final ToolExecutionPlanner toolExecutionPlanner;
    private final ToolMethodRegistry toolMethodRegistry;
    private final ChatClient conversationChatClient;
    private final Executor toolExecutionExecutor;

    public ToolOrchestrator(ToolExecutionPlanner toolExecutionPlanner,
                            ToolMethodRegistry toolMethodRegistry,
                            @Qualifier("conversationChatClient") ChatClient conversationChatClient,
                            @Qualifier("toolExecutionExecutor") Executor toolExecutionExecutor) {
        this.toolExecutionPlanner = toolExecutionPlanner;
        this.toolMethodRegistry = toolMethodRegistry;
        this.conversationChatClient = conversationChatClient;
        this.toolExecutionExecutor = toolExecutionExecutor;
    }

    public ToolOrchestrationResult orchestrate(String conversationId,
                                               String userMessage,
                                               List<String> selectedToolIds,
                                               String systemPrompt) {
        List<ToolExecutionBatch> allBatches = new ArrayList<>();
        List<ToolExecutionOutcome> outcomes = new ArrayList<>();
        boolean usedParallel = false;

        for (int round = 1; round <= MAX_PLANNING_ROUNDS; round++) {
            ToolExecutionPlan roundPlan = toolExecutionPlanner.plan(userMessage, selectedToolIds, outcomes);
            if (!roundPlan.hasSteps()) {
                if (outcomes.isEmpty()) {
                    log.info("No executable orchestration plan. conversationId={}, plannerNote={}, selectedToolIds={}",
                            conversationId, roundPlan.plannerNote(), selectedToolIds);
                    return null;
                }

                log.info("Tool orchestration completed planning loop. conversationId={}, round={}, plannerNote={}",
                        conversationId, round, roundPlan.plannerNote());
                ToolExecutionPlan finalPlan = new ToolExecutionPlan(allBatches, roundPlan.plannerNote());
                String finalReply = summarize(conversationId, userMessage, finalPlan, outcomes, systemPrompt);
                return new ToolOrchestrationResult(finalPlan, outcomes, usedParallel, finalReply);
            }

            log.info("Starting tool orchestration round. conversationId={}, round={}, plannerNote={}, batchCount={}, selectedToolIds={}",
                    conversationId, round, roundPlan.plannerNote(), roundPlan.batches().size(), selectedToolIds);

            List<ToolExecutionBatch> executableBatches = List.of(roundPlan.batches().getFirst());
            if (roundPlan.batches().size() > 1) {
                log.info("Planner returned multiple batches; only the first batch will be executed in this round. conversationId={}, round={}, ignoredBatchCount={}",
                        conversationId, round, roundPlan.batches().size() - 1);
            }

            boolean executedAnyStep = false;
            for (ToolExecutionBatch batch : executableBatches) {
                if (containsExecutedSuccessfulStep(batch, outcomes)) {
                    log.info("Skipping already-completed batch step(s). conversationId={}, round={}, steps={}",
                            conversationId, round, batch.steps().stream().map(ToolExecutionStep::toolName).toList());
                    continue;
                }

                executedAnyStep = true;
                allBatches.add(batch);
                List<ToolExecutionOutcome> batchOutcomes = executeBatch(batch);
                outcomes.addAll(batchOutcomes);
                if (batch.mode() == ExecutionMode.PARALLEL && batch.steps().size() > 1 && isBatchReadOnly(batch)) {
                    usedParallel = true;
                }
            }

            if (!executedAnyStep) {
                log.info("Tool orchestration halted because planner only returned completed steps. conversationId={}, round={}",
                        conversationId, round);
                break;
            }
        }

        if (outcomes.isEmpty()) {
            return null;
        }

        ToolExecutionPlan finalPlan = new ToolExecutionPlan(allBatches, "max-planning-rounds-reached");
        String finalReply = summarize(conversationId, userMessage, finalPlan, outcomes, systemPrompt);
        return new ToolOrchestrationResult(finalPlan, outcomes, usedParallel, finalReply);
    }

    private List<ToolExecutionOutcome> executeBatch(ToolExecutionBatch batch) {
        boolean runParallel = batch.mode() == ExecutionMode.PARALLEL && batch.steps().size() > 1 && isBatchReadOnly(batch);
        if (runParallel) {
            log.info("Executing tool batch in parallel. reason={}, steps={}",
                    batch.reason(), batch.steps().stream().map(ToolExecutionStep::toolName).toList());
            List<CompletableFuture<ToolExecutionOutcome>> futures = batch.steps().stream()
                    .map(step -> CompletableFuture.supplyAsync(() -> invoke(step), toolExecutionExecutor))
                    .toList();
            return futures.stream().map(CompletableFuture::join).toList();
        }

        log.info("Executing tool batch serially. reason={}, steps={}",
                batch.reason(), batch.steps().stream().map(ToolExecutionStep::toolName).toList());
        List<ToolExecutionOutcome> outcomes = new ArrayList<>();
        for (ToolExecutionStep step : batch.steps()) {
            outcomes.add(invoke(step));
        }
        return outcomes;
    }

    private boolean isBatchReadOnly(ToolExecutionBatch batch) {
        return batch.steps().stream()
                .map(step -> toolMethodRegistry.findByCallbackName(step.toolName()))
                .allMatch(definition -> definition != null && definition.readOnly());
    }

    private boolean containsExecutedSuccessfulStep(ToolExecutionBatch batch, List<ToolExecutionOutcome> outcomes) {
        return batch.steps().stream().anyMatch(step -> outcomes.stream().anyMatch(outcome ->
                outcome.success()
                        && outcome.step().toolName().equals(step.toolName())
                        && outcome.step().arguments().equals(step.arguments())));
    }

    private ToolExecutionOutcome invoke(ToolExecutionStep step) {
        ToolMethodDefinition definition = toolMethodRegistry.findByCallbackName(step.toolName());
        if (definition == null) {
            return ToolExecutionOutcome.failure(step, "Unknown tool callback: " + step.toolName());
        }

        try {
            String payload = org.springframework.ai.util.json.JsonParser.toJson(step.arguments());
            String output = definition.callback().call(payload);
            return ToolExecutionOutcome.success(step, output == null ? "" : output);
        }
        catch (Exception e) {
            log.warn("Tool orchestration step failed. toolName={}, purpose={}", step.toolName(), step.purpose(), e);
            return ToolExecutionOutcome.failure(step, e.getMessage());
        }
    }

    private String summarize(String conversationId,
                             String userMessage,
                             ToolExecutionPlan plan,
                             List<ToolExecutionOutcome> outcomes,
                             String systemPrompt) {
        String toolSummary = buildToolSummary(plan, outcomes);
        String reply = conversationChatClient.prompt()
                .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, conversationId))
                .system(buildSummaryPrompt(systemPrompt))
                .user("Original user message:\n" + userMessage + "\n\nTool execution results:\n" + toolSummary)
                .call()
                .content();

        if (!StringUtils.hasText(reply)) {
            return fallbackReply(outcomes);
        }
        return reply.trim();
    }

    private String buildSummaryPrompt(String systemPrompt) {
        return systemPrompt + System.lineSeparator() + """
                The tool execution has already finished.
                Answer the user in Chinese using the tool results below.
                Do not call tools again.
                If a step failed, mention the limitation briefly only when it affects the answer.
                """;
    }

    private String buildToolSummary(ToolExecutionPlan plan, List<ToolExecutionOutcome> outcomes) {
        StringBuilder sb = new StringBuilder();
        sb.append("Planner note: ").append(plan.plannerNote()).append('\n');
        int index = 1;
        for (ToolExecutionOutcome outcome : outcomes) {
            sb.append(index++)
                    .append(". tool=").append(outcome.step().toolName())
                    .append(", purpose=").append(outcome.step().purpose())
                    .append(", success=").append(outcome.success())
                    .append('\n');
            if (outcome.success()) {
                sb.append("output: ").append(outcome.output()).append('\n');
            }
            else {
                sb.append("error: ").append(outcome.errorMessage()).append('\n');
            }
        }
        return sb.toString().trim();
    }

    private String fallbackReply(List<ToolExecutionOutcome> outcomes) {
        return outcomes.stream()
                .filter(ToolExecutionOutcome::success)
                .map(ToolExecutionOutcome::output)
                .filter(StringUtils::hasText)
                .reduce((left, right) -> left + System.lineSeparator() + right)
                .orElse("我暂时没有生成可用回复，请稍后再试。");
    }
}
