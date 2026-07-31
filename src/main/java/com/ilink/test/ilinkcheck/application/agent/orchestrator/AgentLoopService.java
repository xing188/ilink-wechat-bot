package com.ilink.test.ilinkcheck.application.agent.orchestrator;

import com.ilink.test.ilinkcheck.application.agent.orchestrator.model.AgentState;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.model.AgentTaskType;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.model.PhaseExecutionResult;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.model.PhaseExecutor;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.routing.IntentRouter;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.runtime.AgentStateStore;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.runtime.PhaseExecutorRegistry;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.runtime.WorkflowTemplateRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

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

    public AgentLoopService(IntentRouter intentRouter,
                            WorkflowTemplateRegistry workflowTemplateRegistry,
                            PhaseExecutorRegistry phaseExecutorRegistry,
                            AgentStateStore agentStateStore) {
        this.intentRouter = intentRouter;
        this.workflowTemplateRegistry = workflowTemplateRegistry;
        this.phaseExecutorRegistry = phaseExecutorRegistry;
        this.agentStateStore = agentStateStore;
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
            return Optional.of(resumeWorkflow(existingState.get(), normalizedUserMessage));
        }

        Optional<AgentTaskType> taskType = intentRouter.route(normalizedUserMessage);
        if (taskType.isEmpty()) {
            log.info("无法识别当前意图，已进入普通对话。");
            return Optional.empty();
        }

        AgentState state = AgentState.newWorkflow(
                normalizedConversationId,
                taskType.get(),
                workflowTemplateRegistry.startPhase(taskType.get()),
                normalizedUserMessage
        );
        log.info("conversationId={}, agentTaskType={}, workflowEvent=start",
                normalizedConversationId, taskType.get());

        return Optional.of(runWorkflow(state));
    }

    /**
     * 恢复已暂停的工作流
     * 用户在流程等待输入时发送新消息，触发该方法
     * @param state 持久化的会话状态
     * @param userMessage 用户最新输入文本
     * @return 工作流执行后的回复
     */
    private String resumeWorkflow(AgentState state, String userMessage) {
        if (isCancelCommand(userMessage)) {
            agentStateStore.remove(state.getConversationId());
            log.info("conversationId={}, agentTaskType={}, workflowEvent=cancel",
                    state.getConversationId(), state.getTaskType());
            return "已取消当前 Agent 工作流。";
        }

        state.updateLatestUserMessage(userMessage);
        state.clearWaitingUserInput();
        if (state.getCurrentPhase() == com.ilink.test.ilinkcheck.application.agent.orchestrator.model.AgentPhase.WAIT_USER_INPUT) {
            state.resumeFromWaitingUserInput();
        }
        log.info("conversationId={}, agentTaskType={}, currentPhase={}, workflowEvent=resume",
                state.getConversationId(), state.getTaskType(), state.getCurrentPhase());
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
                agentStateStore.remove(state.getConversationId());
                log.info("conversationId={}, agentTaskType={}, workflowEvent=finish, rounds={}",
                        state.getConversationId(), state.getTaskType(), state.getExecutionRound());
                return state.getFinalReply();
            }

            if (result.needUserInput()) {
                com.ilink.test.ilinkcheck.application.agent.orchestrator.model.AgentPhase resumePhase =
                        result.nextPhase() != null ? result.nextPhase() : state.getCurrentPhase();
                state.waitForUserInput(resumePhase);
                agentStateStore.save(state);
                log.info("conversationId={}, agentTaskType={}, currentPhase={}, resumePhase={}, workflowEvent=wait-user-input",
                        state.getConversationId(), state.getTaskType(), state.getCurrentPhase(), resumePhase);
                return StringUtils.hasText(result.replyToUser())
                        ? result.replyToUser()
                        : "请继续提供下一步信息。";
            }

            if (result.nextPhase() == null) {
                break;
            }

            state.moveToPhase(result.nextPhase());
            state.clearWaitingUserInput();
            agentStateStore.save(state);
        }

        state.markFailed("当前 Agent 工作流执行超过最大阶段次数，已停止。");
        agentStateStore.remove(state.getConversationId());
        log.warn("conversationId={}, agentTaskType={}, workflowEvent=fail-max-steps, rounds={}",
                state.getConversationId(), state.getTaskType(), state.getExecutionRound());
        return state.getFinalReply();
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
