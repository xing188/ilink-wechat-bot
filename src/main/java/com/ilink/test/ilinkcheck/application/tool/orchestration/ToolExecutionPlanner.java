package com.ilink.test.ilinkcheck.application.tool.orchestration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class ToolExecutionPlanner {

    private final ChatClient routingChatClient;
    private final ToolMethodRegistry toolMethodRegistry;

    public ToolExecutionPlanner(@Qualifier("routingChatClient") ChatClient routingChatClient,
                                ToolMethodRegistry toolMethodRegistry) {
        this.routingChatClient = routingChatClient;
        this.toolMethodRegistry = toolMethodRegistry;
    }

    public ToolExecutionPlan plan(String userMessage,
                                  List<String> selectedToolIds,
                                  List<ToolExecutionOutcome> completedOutcomes) {
        List<ToolMethodDefinition> definitions = toolMethodRegistry.listForToolIds(selectedToolIds);
        if (definitions.isEmpty() || !StringUtils.hasText(userMessage)) {
            log.info("Tool planner skipped. reason=no-active-tool-methods, selectedToolIds={}", selectedToolIds);
            return new ToolExecutionPlan(List.of(), "no-active-tool-methods");
        }

        String response = routingChatClient.prompt()
                .system(buildPlannerPrompt(selectedToolIds, completedOutcomes))
                .user(buildPlannerUserMessage(userMessage, completedOutcomes))
                .call()
                .content();

        if (!StringUtils.hasText(response)) {
            log.info("Tool planner returned empty response. selectedToolIds={}", selectedToolIds);
            return new ToolExecutionPlan(List.of(), "empty-planner-response");
        }

        String sanitizedResponse = sanitizePlannerResponse(response);

        PlannerResponse plannerResponse;
        try {
            plannerResponse = org.springframework.ai.util.json.JsonParser.fromJson(sanitizedResponse, PlannerResponse.class);
        }
        catch (Exception e) {
            log.info("Tool planner returned invalid response. selectedToolIds={}, rawResponse={}",
                    selectedToolIds, abbreviate(response));
            return new ToolExecutionPlan(List.of(), "invalid-planner-response");
        }

        ToolExecutionPlan plan = normalizePlan(plannerResponse, selectedToolIds);
        log.info("Tool planner produced plan. plannerNote={}, batchCount={}, selectedToolIds={}",
                plan.plannerNote(), plan.batches().size(), selectedToolIds);
        return plan;
    }

    private ToolExecutionPlan normalizePlan(PlannerResponse plannerResponse, List<String> selectedToolIds) {
        if (plannerResponse == null || plannerResponse.batches() == null || plannerResponse.batches().isEmpty()) {
            return new ToolExecutionPlan(List.of(), plannerResponse == null ? "null-planner-response" : plannerResponse.note());
        }

        List<ToolExecutionBatch> batches = new ArrayList<>();
        for (PlannerBatch plannerBatch : plannerResponse.batches()) {
            if (plannerBatch == null || plannerBatch.steps() == null || plannerBatch.steps().isEmpty()) {
                continue;
            }

            List<ToolExecutionStep> steps = plannerBatch.steps().stream()
                    .filter(step -> step != null && StringUtils.hasText(step.toolName()))
                    .map(step -> new ToolExecutionStep(
                            step.toolName().trim(),
                            step.arguments() == null ? Map.of() : step.arguments(),
                            step.purpose() == null ? "" : step.purpose().trim()))
                    .filter(step -> isAllowedTool(step.toolName(), selectedToolIds))
                    .toList();

            if (steps.isEmpty()) {
                continue;
            }

            ExecutionMode requestedMode = "PARALLEL".equalsIgnoreCase(plannerBatch.mode())
                    ? ExecutionMode.PARALLEL
                    : ExecutionMode.SERIAL;
            batches.add(new ToolExecutionBatch(requestedMode, steps, plannerBatch.reason()));
        }

        return new ToolExecutionPlan(batches, plannerResponse.note());
    }

    private boolean isAllowedTool(String callbackName, List<String> selectedToolIds) {
        ToolMethodDefinition definition = toolMethodRegistry.findByCallbackName(callbackName);
        return definition != null && selectedToolIds.contains(definition.toolId());
    }

    private String buildPlannerPrompt(List<String> selectedToolIds, List<ToolExecutionOutcome> completedOutcomes) {
        String catalog = toolMethodRegistry.buildPlannerCatalog(selectedToolIds);
        return """
                You are a tool execution planner for a chat assistant.
                Your job is to transform the latest user message into the NEXT executable tool batch plan.

                Rules:
                1. Return JSON only.
                2. Use only tool names listed in the active catalog.
                3. Plan only the next executable batch. Do not plan future batches whose arguments depend on results you do not have yet.
                4. Use completed tool results when deciding the next step.
                5. Do not repeat already completed successful steps.
                6. Use mode PARALLEL only when every step in that batch is independent.
                7. If the message can be answered without more tools or required parameters are missing, return {"note":"done-or-no-plan","batches":[]}.
                8. Do not invent arguments. Use only arguments grounded in the user message or completed tool results.

                Output JSON schema:
                {
                  "note": "short note",
                  "batches": [
                    {
                      "mode": "SERIAL or PARALLEL",
                      "reason": "why this batch exists",
                      "steps": [
                        {
                          "toolName": "exact callback name",
                          "purpose": "what this step does",
                          "arguments": {"argName": "value"}
                        }
                      ]
                    }
                  ]
                }

                Active tool methods:
                %s

                Completed tool results summary:
                %s
                """.formatted(catalog, formatCompletedOutcomes(completedOutcomes));
    }

    private String buildPlannerUserMessage(String userMessage, List<ToolExecutionOutcome> completedOutcomes) {
        return "User message:\n" + userMessage + "\n\nCompleted tool results:\n" + formatCompletedOutcomes(completedOutcomes);
    }

    private String formatCompletedOutcomes(List<ToolExecutionOutcome> completedOutcomes) {
        if (completedOutcomes == null || completedOutcomes.isEmpty()) {
            return "none";
        }

        StringBuilder sb = new StringBuilder();
        int index = 1;
        for (ToolExecutionOutcome outcome : completedOutcomes) {
            sb.append(index++)
                    .append(". tool=").append(outcome.step().toolName())
                    .append(", success=").append(outcome.success())
                    .append(", purpose=").append(outcome.step().purpose())
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

    private String sanitizePlannerResponse(String response) {
        if (!StringUtils.hasText(response)) {
            return response;
        }

        String sanitized = response.trim();
        if (sanitized.startsWith("```")) {
            sanitized = sanitized.replaceFirst("^```[a-zA-Z]*\\s*", "");
            sanitized = sanitized.replaceFirst("\\s*```$", "");
        }

        int start = sanitized.indexOf('{');
        int end = sanitized.lastIndexOf('}');
        if (start >= 0 && end > start) {
            sanitized = sanitized.substring(start, end + 1);
        }
        return sanitized.trim();
    }

    private String abbreviate(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 300 ? normalized : normalized.substring(0, 300) + "...";
    }
}
