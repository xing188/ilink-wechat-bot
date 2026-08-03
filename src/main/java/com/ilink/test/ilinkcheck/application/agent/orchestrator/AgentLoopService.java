package com.ilink.test.ilinkcheck.application.agent.orchestrator;

import com.ilink.test.ilinkcheck.application.agent.orchestrator.model.AgentArtifacts;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.model.AgentState;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.model.AgentTaskType;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.model.AgentPhase;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.model.PhaseExecutionResult;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.model.PhaseExecutor;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.routing.IntentRouter;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.runtime.AgentStateStore;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.runtime.PhaseExecutorRegistry;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.runtime.WorkflowTemplateRegistry;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.support.AgentTextUtils;
import com.ilink.test.ilinkcheck.infrastructure.monitor.AgentEvent;
import com.ilink.test.ilinkcheck.infrastructure.monitor.AgentEventBus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Optional;

/**
 * Agent 大循环主入口。
 * 这一层只负责状态推进、阶段切换和等待用户输入，不直接写具体业务。
 *
 * 整体运行模型：
 * 1. 收到用户消息，根据会话ID查询是否存在正在运行的Agent工作流
 * 2. 已有工作流：恢复会话状态，继续推进流程（resumeWorkflow）
 * 3. 无工作流：调用意图路由，判断是否需要启动Agent工作流
 * 4. 启动工作流：加载工作流模板，初始化Agent状态，驱动阶段自动执行 runWorkflow
 *
 * PhaseExecutionResult 三种分支逻辑：
 * ① finished = true：任务全部完成，清理会话状态，返回最终回复
 * ② needUserInput = true：流程暂停，持久化会话，等待用户下一条消息继续（Human-in-the-loop）
 * ③ 切换nextPhase：不中断，继续自动执行下一阶段
 */
@Service
@Slf4j
public class AgentLoopService {

    /**
     * 单条工作流最多推进多少个阶段。
     * 这是一个安全兜底，防止因为阶段设计错误出现死循环。
     */
    private static final int MAX_PHASE_STEPS = 8;

    private final IntentRouter intentRouter;
    private final WorkflowTemplateRegistry workflowTemplateRegistry;
    private final PhaseExecutorRegistry phaseExecutorRegistry;
    private final AgentStateStore agentStateStore;

    /** 运行时事件总线（监控台用）。可选注入，未注入时跳过事件发布。 */
    private AgentEventBus agentEventBus;

    public AgentLoopService(IntentRouter intentRouter,
                            WorkflowTemplateRegistry workflowTemplateRegistry,
                            PhaseExecutorRegistry phaseExecutorRegistry,
                            AgentStateStore agentStateStore) {
        this.intentRouter = intentRouter;
        this.workflowTemplateRegistry = workflowTemplateRegistry;
        this.phaseExecutorRegistry = phaseExecutorRegistry;
        this.agentStateStore = agentStateStore;
    }

    @Autowired(required = false)
    public void setAgentEventBus(AgentEventBus agentEventBus) {
        this.agentEventBus = agentEventBus;
    }

    /**
     * 入口方法：尝试让当前消息进入 Agent 工作流。
     * 如果路由器判断这条消息不属于 Agent 任务，就返回 empty，让外层继续走普通对话或 tool 链路。
     */
    public Optional<String> tryHandle(String conversationId, String userMessage) {
        if (!StringUtils.hasText(conversationId) || !StringUtils.hasText(userMessage)) {
            return Optional.empty();
        }

        String normalizedConversationId = conversationId.trim();
        String normalizedUserMessage = userMessage.trim();

        Optional<AgentState> existingState = agentStateStore.findActive(normalizedConversationId);
        if (existingState.isPresent()) {
            AgentState state = existingState.get();
            // 已完成的工作流（技术路线 / GitHub 速读保留态）只响应明确的后续指令；
            // 无关消息（如天气查询等普通请求）直接放行给普通对话/工具链路，
            // 避免被"上一条学习路线已生成完毕"等提示错误拦截。
            if (state.isFinished() && !isFinishedStateFollowUp(state, normalizedUserMessage)) {
                return Optional.empty();
            }
            return Optional.of(resumeWorkflow(state, normalizedConversationId, normalizedUserMessage));
        }

        return Optional.ofNullable(startNewWorkflow(normalizedConversationId, normalizedUserMessage));
    }

    /**
     * 已完成的工作流是否收到明确的后续指令。
     * 只有这类消息才应恢复保留态工作流，其余消息一律放行。
     */
    private boolean isFinishedStateFollowUp(AgentState state, String userMessage) {
        if (isCancelCommand(userMessage)) {
            return true;
        }
        return switch (state.getTaskType()) {
            // 技术路线：翻译成中文 / 新的学习主题
            case TECH_ROADMAP -> AgentTextUtils.wantsChinese(userMessage)
                    || AgentTextUtils.extractTopic(userMessage).isPresent();
            // GitHub 仓库速读：回复目录名继续展开
            case GITHUB_REPO_DIGEST -> AgentTextUtils.isDirectoryPath(userMessage);
            default -> false;
        };
    }

    /**
     * 按路由结果开启一条全新的工作流；若消息不属于任何 Agent 任务则返回 null（交给外层处理）。
     */
    private String startNewWorkflow(String conversationId, String userMessage) {
        Optional<AgentTaskType> taskType = intentRouter.route(userMessage);
        if (taskType.isEmpty()) {
            return null;
        }

        AgentState state = AgentState.newWorkflow(
                conversationId,
                taskType.get(),
                workflowTemplateRegistry.startPhase(taskType.get()),
                userMessage
        );
        log.info("conversationId={}, agentTaskType={}, workflowEvent=start", conversationId, taskType.get());
        publishEvent(state, "start", null);
        return runWorkflow(state);
    }

    /**
     * 继续执行已经进入等待态、或已完成（如技术路线）的工作流。
     */
    private String resumeWorkflow(AgentState state, String conversationId, String userMessage) {
        if (isCancelCommand(userMessage)) {
            agentStateStore.remove(state.getConversationId());
            log.info("conversationId={}, agentTaskType={}, workflowEvent=cancel",
                    state.getConversationId(), state.getTaskType());
            publishEvent(state, "cancel", "已取消当前 Agent 工作流。");
            return "已取消当前 Agent 工作流。";
        }

        Optional<AgentTaskType> reroutedTask = intentRouter.route(userMessage);
        if (reroutedTask.isPresent() && reroutedTask.get() != state.getTaskType()) {
            agentStateStore.remove(state.getConversationId());
            log.info("conversationId={}, fromTaskType={}, toTaskType={}, workflowEvent=route-jump",
                    state.getConversationId(), state.getTaskType(), reroutedTask.get());
            publishEvent(state, "route-jump", "切换到 " + reroutedTask.get().name());
            String fresh = startNewWorkflow(conversationId, userMessage);
            return fresh != null ? fresh : "我理解了你的新请求，但暂时没能启动对应流程，请再说具体一点。";
        }

        // 新的学习请求：丢弃旧路线工作流，开启一条同类型的新路线
        if (state.getTaskType() == AgentTaskType.TECH_ROADMAP
                && AgentTextUtils.extractTopic(userMessage).isPresent()) {
            agentStateStore.remove(state.getConversationId());
            log.info("conversationId={}, agentTaskType={}, workflowEvent=restart",
                    state.getConversationId(), state.getTaskType());
            publishEvent(state, "restart", "重新开始新的学习路线");
            String fresh = startNewWorkflow(conversationId, userMessage);
            return fresh != null ? fresh : "已重新开始，请告诉我你想学的技术主题。";
        }

        // 后续指令：把已生成的技术路线翻译成中文版（复用上次取到的资料重新渲染）
        if (state.getTaskType() == AgentTaskType.TECH_ROADMAP && AgentTextUtils.wantsChinese(userMessage)) {
            state.putArtifact(AgentArtifacts.ROADMAP_TRANSLATE, true);
            state.updateLatestUserMessage(userMessage);
            state.clearWaitingUserInput();
            state.moveToPhase(AgentPhase.ROADMAP_COMPOSE);
            log.info("conversationId={}, agentTaskType={}, workflowEvent=translate",
                    state.getConversationId(), state.getTaskType());
            publishEvent(state, "translate", "将学习路线翻译成中文");
            return runWorkflow(state);
        }

        // 已完成的工作流收到无关消息：给出友好提示，避免误入 FINISH 阶段执行器
        if (state.isFinished()) {
            // GitHub 仓库速读：回复目录名 → 继续展开目录分析
            if (state.getTaskType() == AgentTaskType.GITHUB_REPO_DIGEST
                    && AgentTextUtils.isDirectoryPath(userMessage)) {
                state.updateLatestUserMessage(userMessage);
                state.clearWaitingUserInput();
                state.moveToPhase(AgentPhase.GITHUB_DIRECTORY_EXPAND);
                log.info("conversationId={}, agentTaskType={}, workflowEvent=expand-directory",
                        state.getConversationId(), state.getTaskType());
                publishEvent(state, "expand-directory", "展开目录：" + userMessage);
                return runWorkflow(state);
            }
            return "上一条学习路线已生成完毕。回复“翻译成中文”可切换为中文版，或发送新的“我想学 X”开始新路线。";
        }

        state.updateLatestUserMessage(userMessage);
        state.clearWaitingUserInput();
        if (state.getCurrentPhase() == com.ilink.test.ilinkcheck.application.agent.orchestrator.model.AgentPhase.WAIT_USER_INPUT) {
            state.resumeFromWaitingUserInput();
        }
        log.info("conversationId={}, agentTaskType={}, currentPhase={}, workflowEvent=resume",
                state.getConversationId(), state.getTaskType(), state.getCurrentPhase());
        publishEvent(state, "resume", null);
        return runWorkflow(state);
    }

    /**
     * 按阶段推进当前工作流。
     * 真正的业务逻辑在各个 PhaseExecutor 里，这里只负责调度和状态维护。
     */
    private String runWorkflow(AgentState state) {
        for (int i = 0; i < MAX_PHASE_STEPS; i++) {
            state.incrementExecutionRound();

            PhaseExecutor executor = phaseExecutorRegistry.findRequired(state.getTaskType(), state.getCurrentPhase());
            PhaseExecutionResult result = executor.execute(state);
            state.mergeArtifacts(result.artifactsDelta());

            if (result.finished()) {
                state.markFinished(result.replyToUser());
                // 技术路线 / GitHub 仓库速读完成后保留状态，以支持后续"翻译成中文"、"目录展开"等指令；
                // 其它工作流完成后即从存储中移除。
                if (state.getTaskType() != AgentTaskType.TECH_ROADMAP
                        && state.getTaskType() != AgentTaskType.GITHUB_REPO_DIGEST) {
                    agentStateStore.remove(state.getConversationId());
                }
                log.info("conversationId={}, agentTaskType={}, workflowEvent=finish, rounds={}",
                        state.getConversationId(), state.getTaskType(), state.getExecutionRound());
                publishEvent(state, "finish", state.getFinalReply());
                return state.getFinalReply();
            }

            if (result.needUserInput()) {
                com.ilink.test.ilinkcheck.application.agent.orchestrator.model.AgentPhase resumePhase =
                        result.nextPhase() != null ? result.nextPhase() : state.getCurrentPhase();
                state.waitForUserInput(resumePhase);
                agentStateStore.save(state);
                log.info("conversationId={}, agentTaskType={}, currentPhase={}, resumePhase={}, workflowEvent=wait-user-input",
                        state.getConversationId(), state.getTaskType(), state.getCurrentPhase(), resumePhase);
                publishEvent(state, "wait-user-input", result.replyToUser());
                return StringUtils.hasText(result.replyToUser())
                        ? result.replyToUser()
                        : "请继续提供下一步信息。";
            }

            if (result.nextPhase() == null) {
                break;
            }

            publishEvent(state, "phase", result.replyToUser());
            state.moveToPhase(result.nextPhase());
            state.clearWaitingUserInput();
            agentStateStore.save(state);
        }

        state.markFailed("当前 Agent 工作流执行超过最大阶段次数，已停止。");
        agentStateStore.remove(state.getConversationId());
        log.warn("conversationId={}, agentTaskType={}, workflowEvent=fail-max-steps, rounds={}",
                state.getConversationId(), state.getTaskType(), state.getExecutionRound());
        publishEvent(state, "fail", state.getFinalReply());
        return state.getFinalReply();
    }

    /**
     * 发布 Agent 运行时事件（监控台用）。事件总线未注入时静默跳过。
     */
    private void publishEvent(AgentState state, String eventName, String reply) {
        if (agentEventBus == null) {
            return;
        }
        agentEventBus.publish(new AgentEvent(
                Instant.now(),
                state.getConversationId(),
                state.getTaskType(),
                state.getCurrentPhase(),
                eventName,
                truncate(state.getLatestUserMessage(), 120),
                truncate(reply, 200),
                state.getArtifact(AgentArtifacts.REQUEST_TOPIC, String.class),
                state.getArtifact(AgentArtifacts.REQUEST_REPOSITORY, String.class),
                truncate(state.getArtifact(AgentArtifacts.REQUEST_DRAFT, String.class), 100),
                state.getArtifact(AgentArtifacts.REQUEST_SCHEDULE_TIME, String.class)
        ));
    }

    private String truncate(String text, int max) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        String trimmed = text.trim();
        if (trimmed.length() <= max) {
            return trimmed;
        }
        return trimmed.substring(0, max) + "…";
    }

    /**
     * 用户在多轮工作流里可随时用这些关键词取消。
     */
    private boolean isCancelCommand(String userMessage) {
        return "取消".equals(userMessage)
                || "退出".equals(userMessage)
                || "停止".equals(userMessage)
                || "算了".equals(userMessage);
    }
}
