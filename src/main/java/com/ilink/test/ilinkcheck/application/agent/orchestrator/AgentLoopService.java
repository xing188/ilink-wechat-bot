package com.ilink.test.ilinkcheck.application.agent.orchestrator;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Optional;

/**
 * Agent 大循环主入口。
 * 这一层只做状态推进和阶段调度，不直接写具体业务细节。
 */
@Service
@Slf4j
public class AgentLoopService {

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
     * 尝试用 Agent 工作流处理当前消息。
     * 返回 empty 表示这条消息不属于 Agent 路由，外层可以继续走普通对话 / 工具链路。
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
                        : "请继续提供下一步指令。";
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

    private boolean isCancelCommand(String userMessage) {
        return "取消".equals(userMessage)
                || "退出".equals(userMessage)
                || "停止".equals(userMessage)
                || "算了".equals(userMessage);
    }
}
