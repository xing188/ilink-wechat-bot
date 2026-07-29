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
     * 尝试让当前消息进入 Agent 工作流。
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
     * 继续执行已经进入等待态的工作流。
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
                if (result.nextPhase() != null) {
                    state.moveToPhase(result.nextPhase());
                }
                state.waitForUserInput();
                agentStateStore.save(state);
                log.info("conversationId={}, agentTaskType={}, currentPhase={}, workflowEvent=wait-user-input",
                        state.getConversationId(), state.getTaskType(), state.getCurrentPhase());
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
