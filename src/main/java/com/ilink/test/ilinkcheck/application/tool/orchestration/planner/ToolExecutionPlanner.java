package com.ilink.test.ilinkcheck.application.tool.orchestration.planner;

import com.ilink.test.ilinkcheck.application.tool.orchestration.meta.ToolMethodDefinition;
import com.ilink.test.ilinkcheck.application.tool.orchestration.meta.ToolMethodRegistry;
import com.ilink.test.ilinkcheck.application.tool.orchestration.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.util.json.JsonParser;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
/**
 * 【工具执行规划器｜Agent ReAct思考单元】
 * 职责：调用独立路由LLM，基于用户提问 + 历史工具执行结果，生成【下一轮可执行工具批次计划】
 *
 * 核心保守设计思想：
 * 只规划「下一批」可执行任务，不一次性生成整条完整工作流。
 * 当任务存在数据依赖（A执行结果作为B入参），必须等A真实执行完成拿到结果，
 * 进入下一轮循环再次调用本规划器生成B的调用计划，大幅降低模型幻觉。
 *
 * 输出产物：ToolExecutionPlan（包含多个执行批次Batch；Orchestrator每轮只执行第一个Batch）
 */
public class ToolExecutionPlanner {

    /**
     * 独立LLM客户端：专门用于工具规划，和对话汇总ChatClient物理隔离
     * 好处：可单独配置模型、温度、超时；规划任务不占用对话推理资源
     */
    private final ChatClient routingChatClient;
    /**
     * 工具元数据注册中心：获取本轮放行工具清单、工具只读属性、描述、入参Schema
     */
    private final ToolMethodRegistry toolMethodRegistry;

    public ToolExecutionPlanner(@Qualifier("routingChatClient") ChatClient routingChatClient,
                                ToolMethodRegistry toolMethodRegistry) {
        this.routingChatClient = routingChatClient;
        this.toolMethodRegistry = toolMethodRegistry;
    }

    /**
     * 规划入口：为当前这一轮生成一个归一化后的 plan。
     * completedOutcomes 表示前面轮次已经执行出的真实结果，
     * planner 可以基于这些结果继续做条件规划。
     *
     * @param userMessage 用户原始消息
     * @param selectedToolIds ToolSelector筛选后的、本轮允许开放的业务工具ID
     * @param completedOutcomes 之前轮次已经执行完成的步骤结果
     * @return 当前这一轮的归一化计划
     */
    public ToolExecutionPlan plan(String userMessage,
                                  List<String> selectedToolIds,
                                  List<ToolExecutionOutcome> completedOutcomes) {
        // 校验：没有可用工具 / 用户消息为空，直接返回空计划，无需调用LLM
        List<ToolMethodDefinition> definitions = toolMethodRegistry.listForToolIds(selectedToolIds);
        if (definitions.isEmpty() || !StringUtils.hasText(userMessage)) {
            log.info("工具规划器跳过执行。reason=no-active-tool-methods, selectedToolIds={}", selectedToolIds);
            return new ToolExecutionPlan(List.of(), "no-active-tool-methods");
        }

        // 请求规划专用LLM，传入规划提示词 + 上下文
        String response = routingChatClient.prompt()
                .system(buildPlannerPrompt(selectedToolIds, completedOutcomes))
                .user(buildPlannerUserMessage(userMessage, completedOutcomes))
                .call()
                .content();
        // LLM返回空内容，返回空计划
        if (!StringUtils.hasText(response)) {
            log.info("工具规划器返回空响应。selectedToolIds={}", selectedToolIds);
            return new ToolExecutionPlan(List.of(), "empty-planner-response");
        }

        // 清洗返回文本：剥离```json 代码块，提取纯JSON
        String sanitizedResponse = sanitizePlannerResponse(response);

        PlannerResponse plannerResponse;
        try {
            // JSON反序列化为规划器DTO
            plannerResponse = JsonParser.fromJson(sanitizedResponse, PlannerResponse.class);
        }
        catch (Exception e) {
            log.info("工具规划器返回了无法解析的响应。selectedToolIds={}, rawResponse={}",
                    selectedToolIds, abbreviate(response));
            return new ToolExecutionPlan(List.of(), "invalid-planner-response");
        }

        // 【安全归一化阶段】过滤非法工具、无效步骤，生成安全可执行Plan
        ToolExecutionPlan plan = normalizePlan(plannerResponse, selectedToolIds);
        log.info("工具规划器成功产出计划。plannerNote={}, batchCount={}, selectedToolIds={}",
                plan.plannerNote(), plan.batches().size(), selectedToolIds);
        return plan;
    }

    /**
     * 归一化处理：对模型输出的原始计划做安全校验、清洗、转换
     * 解决风险：模型可能臆造不存在工具、输出本轮未授权工具、空步骤
     * @param plannerResponse LLM原始返回DTO
     * @param selectedToolIds 当前放行业务工具ID列表，用于权限校验
     * @return 清洗完成、可安全交给Orchestrator执行的执行计划
     */
    private ToolExecutionPlan normalizePlan(PlannerResponse plannerResponse, List<String> selectedToolIds) {
        if (plannerResponse == null || plannerResponse.batches() == null || plannerResponse.batches().isEmpty()) {
            return new ToolExecutionPlan(List.of(), plannerResponse == null ? "null-planner-response" : plannerResponse.note());
        }

        List<ToolExecutionBatch> batches = new ArrayList<>();
        // 遍历模型输出所有批次
        for (PlannerBatch plannerBatch : plannerResponse.batches()) {
            if (plannerBatch == null || plannerBatch.steps() == null || plannerBatch.steps().isEmpty()) {
                continue;
            }

            // 转换Step，并且过滤【不在权限范围内的非法工具】
            List<ToolExecutionStep> steps = plannerBatch.steps().stream()
                    .filter(step -> step != null && StringUtils.hasText(step.toolName()))
                    .map(step -> new ToolExecutionStep(
                            step.toolName().trim(),
                            step.arguments() == null ? Map.of() : step.arguments(),
                            step.purpose() == null ? "" : step.purpose().trim()))
                    .filter(step -> isAllowedTool(step.toolName(), selectedToolIds))
                    .toList();

            // 过滤后批次内无合法步骤，直接丢弃该批次
            if (steps.isEmpty()) {
                continue;
            }

            // 解析串行/并行标记；模型识别大小写不敏感，未知模式默认降级SERIAL串行
            ExecutionMode requestedMode = "PARALLEL".equalsIgnoreCase(plannerBatch.mode())
                    ? ExecutionMode.PARALLEL
                    : ExecutionMode.SERIAL;
            batches.add(new ToolExecutionBatch(requestedMode, steps, plannerBatch.reason()));
        }

        return new ToolExecutionPlan(batches, plannerResponse.note());
    }

    /**
     * 权限校验：判断模型输出的callbackName是否允许调用
     * 逻辑：根据callbackName找到定义，校验归属的toolId是否在本轮放行列表中
     * @param callbackName LLM输出工具方法名
     * @param selectedToolIds 本轮放行业务工具ID集合
     * @return true=允许执行；false=非法工具，需要过滤
     */
    private boolean isAllowedTool(String callbackName, List<String> selectedToolIds) {
        ToolMethodDefinition definition = toolMethodRegistry.findByCallbackName(callbackName);
        // 工具存在 && 工具归属的业务toolId在选中列表内
        return definition != null && selectedToolIds.contains(definition.toolId());
    }

    /**
     * planner 的系统提示词。
     * 重点要求模型只规划“下一轮可执行批次”，不要一次性展开整棵工作流树。
     * 约束LLM输出规范、规划策略；同时注入【可用工具完整目录（包含只读标记）】
     * @param selectedToolIds 当前可用工具集合
     * @param completedOutcomes 之前轮次的执行结果
     * @return planner 的系统提示词
     */
    private String buildPlannerPrompt(List<String> selectedToolIds, List<ToolExecutionOutcome> completedOutcomes) {
        String catalog = toolMethodRegistry.buildPlannerCatalog(selectedToolIds);
        return """
                你是聊天助手的工具执行规划器。
                你的任务是把用户当前请求，转成“下一轮就能执行”的工具批次计划。

                规则：
                1. 只能返回 JSON，不要输出解释性文字。
                2. 只能使用当前工具目录里列出的 callback 名称。
                3. 只规划下一轮可执行批次，不要提前规划依赖未来结果的后续批次。
                4. 你可以参考已经完成的工具结果，决定下一步该调用什么。
                5. 不要重复规划已经成功执行过的步骤。
                6. 只有当一个批次里的步骤彼此独立时，才允许把 mode 设为 PARALLEL。
                7. 如果当前已经不需要更多工具，或者缺少必要参数无法继续，请返回 {"note":"done-or-no-plan","batches":[]}。
                8. 不要臆造参数，只能使用用户消息或已完成工具结果里能够明确支撑的参数。

                输出 JSON 结构：
                {
                  "note": "简短说明",
                  "batches": [
                    {
                      "mode": "SERIAL 或 PARALLEL",
                      "reason": "这一批次存在的原因",
                      "steps": [
                        {
                          "toolName": "精确的 callback 名称",
                          "purpose": "这一步的目的",
                          "arguments": {"参数名": "参数值"}
                        }
                      ]
                    }
                  ]
                }

                当前可用工具方法目录：
                %s

                已完成工具结果摘要：
                %s
                """.formatted(catalog, formatCompletedOutcomes(completedOutcomes));
    }

    /**
     * 组装User侧Prompt：原始用户问题 + 历史工具执行记录
     *
     * @param userMessage 用户原始消息
     * @param completedOutcomes 已完成步骤的结果列表
     * @return 提供给 planner 的 user 文本
     */
    private String buildPlannerUserMessage(String userMessage, List<ToolExecutionOutcome> completedOutcomes) {
        return "用户原始请求：\n" + userMessage + "\n\n已完成工具结果：\n" + formatCompletedOutcomes(completedOutcomes);
    }

    /**
     * 将历史工具执行结果格式化文本，送入LLM作为上下文
     *
     * @param completedOutcomes 已完成步骤结果
     * @return 供 planner 阅读的文本摘要
     */
    private String formatCompletedOutcomes(List<ToolExecutionOutcome> completedOutcomes) {
        if (completedOutcomes == null || completedOutcomes.isEmpty()) {
            return "无";
        }

        StringBuilder sb = new StringBuilder();
        int index = 1;
        for (ToolExecutionOutcome outcome : completedOutcomes) {
            sb.append(index++)
                    .append(". 工具=").append(outcome.step().toolName())
                    .append("，成功=").append(outcome.success())
                    .append("，目的=").append(outcome.step().purpose())
                    .append('\n');
            if (outcome.success()) {
                sb.append("输出：").append(outcome.output()).append('\n');
            }
            else {
                sb.append("错误：").append(outcome.errorMessage()).append('\n');
            }
        }
        return sb.toString().trim();
    }

    /**
     * 模型经常把 JSON 包在 ```json 代码块里，截取{}内纯JSON
     * 这里负责去掉包裹层，只保留最外层 JSON 对象。
     *
     * @param response 模型原始返回文本
     * @return 尽量可解析的 JSON 文本
     */
    private String sanitizePlannerResponse(String response) {
        if (!StringUtils.hasText(response)) {
            return response;
        }

        String sanitized = response.trim();
        // 去除开头 ```json / ```
        if (sanitized.startsWith("```")) {
            sanitized = sanitized.replaceFirst("^```[a-zA-Z]*\\s*", "");
            sanitized = sanitized.replaceFirst("\\s*```$", "");
        }

        // 精准截取第一个{ 和最后一个}，剔除前后多余闲聊文字
        int start = sanitized.indexOf('{');
        int end = sanitized.lastIndexOf('}');
        if (start >= 0 && end > start) {
            sanitized = sanitized.substring(start, end + 1);
        }
        return sanitized.trim();
    }

    /**
     * 截断过长日志文本原始响应，避免日志刷屏。
     *
     * @param text 原始文本
     * @return 截断后的日志友好文本
     */
    private String abbreviate(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 300 ? normalized : normalized.substring(0, 300) + "...";
    }
}
