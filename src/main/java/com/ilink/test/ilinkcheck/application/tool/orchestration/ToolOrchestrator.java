package com.ilink.test.ilinkcheck.application.tool.orchestration;

import com.ilink.test.ilinkcheck.application.tool.orchestration.meta.ToolMethodDefinition;
import com.ilink.test.ilinkcheck.application.tool.orchestration.meta.ToolMethodRegistry;
import com.ilink.test.ilinkcheck.application.tool.orchestration.model.*;
import com.ilink.test.ilinkcheck.application.tool.orchestration.planner.ToolExecutionPlanner;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.util.json.JsonParser;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * 【应用层工具编排器｜单次用户请求内的Agent循环调度核心】
 * 职责定位：处于【ToolSelector工具筛选层】和【最终回复生成】中间层
 * 完整工作流：
 * 1. 循环调用Planner，基于历史工具执行结果，生成下一轮工具执行计划
 * 2. 按照串行/并行策略执行批次内工具；内置安全策略：写操作强制串行，不受Planner输出影响
 * 3. 收集工具返回结果，回填上下文，进入下一轮规划
 * 4. 规划终止/达到最大轮次后，调用LLM汇总所有工具输出，生成面向用户自然语言回答
 *
 * 重要边界说明：
 * 这是【单次用户会话请求内部的局部Agent循环】，不是跨请求全局常驻Agent；
 * 一次微信消息触发一次 orchestrate()，循环执行工具调用，全部结束后输出一条最终回复。
 */
@Service
@Slf4j
public class ToolOrchestrator {

    /**
     * 单次用户请求最大规划轮次，安全兜底阈值
     * 防止任务无限迭代、持续调用LLM与工具，控制成本与超时风险
     */
    private static final int MAX_PLANNING_ROUNDS = 4;

    /**
     * 工具规划器：负责调用独立LLM，生成结构化执行计划Batch/Step
     */
    private final ToolExecutionPlanner toolExecutionPlanner;
    /**
     * 工具元数据注册中心：维护全部callback元信息、只读标记、ToolCallback实例
     */
    private final ToolMethodRegistry toolMethodRegistry;
    /**
     * 对话汇总专用LLM客户端
     * 工具执行完毕后，单独调用该客户端整合全部结果生成最终回复
     * 与routingChatClient（规划器模型）隔离，可独立调参
     */
    private final ChatClient conversationChatClient;
    /**
     * 工具并行执行专用线程池，隔离主线程/消息接收线程，避免阻塞
     */
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

    /**
     * 编排入口方法，启动单次请求内的Agent迭代循环
     * @param conversationId 唯一会话标识，绑定会话记忆
     * @param userMessage 用户原始提问文本
     * @param selectedToolIds ToolSelector筛选放行的【粗粒度toolId集合】
     * @param systemPrompt 对话主链路基础系统提示词
     * @return 编排结果：包含完整执行计划、所有工具输出、是否使用并行、最终回复；无可用工具返回null
     */
    public ToolOrchestrationResult orchestrate(String conversationId,
                                               String userMessage,
                                               List<String> selectedToolIds,
                                               String systemPrompt) {
        // 收集本轮请求所有执行过的工具批次，用于最终归档
        List<ToolExecutionBatch> allBatches = new ArrayList<>();
        // 全局容器：保存所有轮次工具执行结果（成功/失败、返回值、异常），持续传给下一轮Planner
        List<ToolExecutionOutcome> outcomes = new ArrayList<>();
        // 标记本轮是否启用并行执行，用于指标统计、日志观测
        boolean usedParallel = false;

        for (int round = 1; round <= MAX_PLANNING_ROUNDS; round++) {
            // 核心：每一轮都会把已有的工具执行结果outcomes传给规划器，让LLM基于真实输出重新思考下一步
            ToolExecutionPlan roundPlan = toolExecutionPlanner.plan(userMessage, selectedToolIds, outcomes);
            // 分支：规划器返回无任何可执行步骤，代表任务可以结束，进入汇总阶段
            if (!roundPlan.hasSteps()) {
                // 从未执行任何工具，直接返回null，上层走纯对话逻辑，不进入工具汇总
                if (outcomes.isEmpty()) {
                    log.info("没有得到可执行的编排计划。conversationId={}, plannerNote={}, selectedToolIds={}",
                            conversationId, roundPlan.plannerNote(), selectedToolIds);
                    return null;
                }

                log.info("工具编排结束规划循环。conversationId={}, round={}, plannerNote={}",
                        conversationId, round, roundPlan.plannerNote());
                ToolExecutionPlan finalPlan = new ToolExecutionPlan(allBatches, roundPlan.plannerNote());
                String finalReply = summarize(conversationId, userMessage, finalPlan, outcomes, systemPrompt);
                return new ToolOrchestrationResult(finalPlan, outcomes, usedParallel, finalReply);
            }

            log.info("开始工具编排轮次。conversationId={}, round={}, plannerNote={}, batchCount={}, selectedToolIds={}",
                    conversationId, round, roundPlan.plannerNote(), roundPlan.batches().size(), selectedToolIds);

            /**
             * 【保守策略核心设计】
             * Planner可能一次性输出多个Batch（代表模型预判的后续任务）
             * 本轮仅执行第一个Batch；剩余批次全部丢弃，不缓存、不续跑
             * 设计思想：不信任模型在缺少真实执行结果时预判后续流程；
             * 等待第一批工具执行完成，携带真实数据重新规划，降低模型幻觉、参数缺失风险
             */
            List<ToolExecutionBatch> executableBatches = List.of(roundPlan.batches().getFirst());
            if (roundPlan.batches().size() > 1) {
                log.info("planner 返回了多个批次，但本轮只执行第一个。conversationId={}, round={}, ignoredBatchCount={}",
                        conversationId, round, roundPlan.batches().size() - 1);
            }

            boolean executedAnyStep = false;
            // 遍历本轮待执行批次（当前策略下永远只有1个batch）
            for (ToolExecutionBatch batch : executableBatches) {
                // 幂等校验：跳过已经成功执行过【相同工具+相同入参】的步骤，防止重复调用工具
                if (containsExecutedSuccessfulStep(batch, outcomes)) {
                    log.info("跳过已经成功执行过的批次步骤。conversationId={}, round={}, steps={}",
                            conversationId, round, batch.steps().stream().map(ToolExecutionStep::toolName).toList());
                    continue;
                }

                executedAnyStep = true;
                allBatches.add(batch);
                // 执行批次内全部工具（自动选择串行/并行）
                List<ToolExecutionOutcome> batchOutcomes = executeBatch(batch);
                outcomes.addAll(batchOutcomes);

                // 标记是否触发并行：批次模式为PARALLEL、工具数量>1、全部工具为只读
                if (batch.mode() == ExecutionMode.PARALLEL && batch.steps().size() > 1 && isBatchReadOnly(batch)) {
                    usedParallel = true;
                }
            }

            // 当前轮没有执行任何新任务，代表剩余计划全部已经执行完毕，终止循环
            if (!executedAnyStep) {
                log.info("工具编排停止，因为 planner 返回的都是已完成步骤。conversationId={}, round={}",
                        conversationId, round);
                break;
            }
        }

        // 达到最大轮次强制退出兜底
        if (outcomes.isEmpty()) {
            return null;
        }

        ToolExecutionPlan finalPlan = new ToolExecutionPlan(allBatches, "max-planning-rounds-reached");
        String finalReply = summarize(conversationId, userMessage, finalPlan, outcomes, systemPrompt);
        return new ToolOrchestrationResult(finalPlan, outcomes, usedParallel, finalReply);
    }

    /**
     * 执行单个批次内所有工具，自动判定串行/并行模式
     * 并行开启存在双重校验：Planner输出PARALLEL + 批次所有工具都是只读
     * 即使LLM要求并行，只要存在写操作工具，强制降级串行，保障数据安全
     * @param batch 待执行工具批次
     * @return 批次内所有步骤执行结果集合
     */
    private List<ToolExecutionOutcome> executeBatch(ToolExecutionBatch batch) {
        boolean runParallel = batch.mode() == ExecutionMode.PARALLEL && batch.steps().size() > 1 && isBatchReadOnly(batch);
        if (runParallel) {
            log.info("以并行方式执行工具批次。reason={}, steps={}",
                    batch.reason(), batch.steps().stream().map(ToolExecutionStep::toolName).toList());
            // 使用专属线程池异步执行每个工具Step
            List<CompletableFuture<ToolExecutionOutcome>> futures = batch.steps().stream()
                    .map(step -> CompletableFuture.supplyAsync(() -> invoke(step), toolExecutionExecutor))
                    .toList();
            // 阻塞等待全部并行任务完成后汇总结果
            return futures.stream().map(CompletableFuture::join).toList();
        }

        log.info("以串行方式执行工具批次。reason={}, steps={}",
                batch.reason(), batch.steps().stream().map(ToolExecutionStep::toolName).toList());
        List<ToolExecutionOutcome> outcomes = new ArrayList<>();
        for (ToolExecutionStep step : batch.steps()) {
            outcomes.add(invoke(step));
        }
        return outcomes;
    }

    /**
     * 只要方法存在副作用，就绝不允许并行执行。
     * 即使 planner 说要并行，这里也会强制降级成串行。
     * @param batch 待判断的批次
     * @return 当前批次是否全部由只读步骤组成
     */
    private boolean isBatchReadOnly(ToolExecutionBatch batch) {
        return batch.steps().stream()
                .map(step -> toolMethodRegistry.findByCallbackName(step.toolName()))
                .allMatch(definition -> definition != null && definition.readOnly());
    }

    /**
     * 幂等判断：批次内是否存在已经成功执行完成的相同任务
     * 匹配规则：工具名称callbackName 完全一致 + 入参arguments完全相等
     * 避免重复调用创建提醒、记账这类有副作用工具
     * @param batch 当前待执行批次
     * @param outcomes 历史全部执行结果
     * @return true=存在已完成任务，应当跳过该批次
     */
    private boolean containsExecutedSuccessfulStep(ToolExecutionBatch batch, List<ToolExecutionOutcome> outcomes) {
        return batch.steps().stream().anyMatch(step -> outcomes.stream().anyMatch(outcome ->
                outcome.success()
                        && outcome.step().toolName().equals(step.toolName())
                        && outcome.step().arguments().equals(step.arguments())));
    }

    /**
     *  执行单个工具调用步骤
     *  根据step内的callbackName查找SpringAI原生ToolCallback，发起调用
     * @param step 当前要执行的步骤
     * @return 该步骤的执行结果
     */
    private ToolExecutionOutcome invoke(ToolExecutionStep step) {
        ToolMethodDefinition definition = toolMethodRegistry.findByCallbackName(step.toolName());
        if (definition == null) {
            return ToolExecutionOutcome.failure(step, "Unknown tool callback: " + step.toolName());
        }

        try {
            String payload = JsonParser.toJson(step.arguments());
            String output = definition.callback().call(payload);
            return ToolExecutionOutcome.success(step, output);
        } catch (Exception e) {
            log.warn("Tool orchestration step failed. toolName={}, purpose={}", step.toolName(), step.purpose(), e);
            return ToolExecutionOutcome.failure(step, e.getMessage());
        }
    }

    /**
     * 【汇总阶段】
     * 所有工具执行全部结束，调用独立LLM整合全部工具输出，生成面向用户的自然语言回复
     * 强制约束：汇总阶段禁止再次发起任何工具调用
     * @param conversationId 当前会话id
     * @param userMessage 用户原始消息
     * @param plan 完整工具执行计划
     * @param outcomes 所有工具执行结果
     * @param systemPrompt 主链路系统提示词
     * @return 最终回复文本
     */
    private String summarize(String conversationId,
                             String userMessage,
                             ToolExecutionPlan plan,
                             List<ToolExecutionOutcome> outcomes,
                             String systemPrompt) {
        String toolSummary = buildToolSummary(plan, outcomes);
        String reply = conversationChatClient.prompt()
                // 绑定会话记忆advisor
                .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, conversationId))
                .system(buildSummaryPrompt(systemPrompt))
                .user("用户原始请求：\n" + userMessage + "\n\n工具执行结果：\n" + toolSummary)
                .call()
                .content();

        // LLM返回空内容，启用兜底策略
        if (!StringUtils.hasText(reply)) {
            return fallbackReply(outcomes);
        }
        return reply.trim();
    }

    /**
     * 总结阶段的 prompt 明确禁止再次调工具。
     *
     * @param systemPrompt 主链路已有的系统提示词
     * @return 给总结模型使用的系统提示词
     */
    private String buildSummaryPrompt(String systemPrompt) {
        return systemPrompt + System.lineSeparator() + """
                工具执行已经完成。
                请基于下面给出的工具结果，用中文回答用户。
                不要再次调用任何工具。
                如果某一步失败了，只有在它确实影响最终回答时，才简短说明限制。
                """;
    }

    /**
     * 把计划和执行结果转成摘要文本，交给总结模型LLM读取。
     * @param plan 最终执行计划
     * @param outcomes 所有执行结果
     * @return 总结模型可直接读取的摘要文本
     */
    private String buildToolSummary(ToolExecutionPlan plan, List<ToolExecutionOutcome> outcomes) {
        StringBuilder sb = new StringBuilder();
        sb.append("规划备注：").append(plan.plannerNote()).append('\n');
        int index = 1;
        for (ToolExecutionOutcome outcome : outcomes) {
            sb.append(index++)
                    .append(". 工具=").append(outcome.step().toolName())
                    .append("，目的=").append(outcome.step().purpose())
                    .append("，成功=").append(outcome.success())
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
     * 只有当总结模型没有返回文本时，才使用这个兜底输出。
     *
     * @param outcomes 所有执行结果
     * @return 兜底回复文本
     */
    private String fallbackReply(List<ToolExecutionOutcome> outcomes) {
        return outcomes.stream()
                .filter(ToolExecutionOutcome::success)
                .map(ToolExecutionOutcome::output)
                .filter(StringUtils::hasText)
                .reduce((left, right) -> left + System.lineSeparator() + right)
                .orElse("我暂时没有生成可用回复，请稍后再试。");
    }
}
