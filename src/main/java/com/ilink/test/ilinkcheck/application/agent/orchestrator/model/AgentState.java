package com.ilink.test.ilinkcheck.application.agent.orchestrator.model;

import lombok.Getter;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 单条 Agent 工作流的运行时状态。
 * 第一版先使用内存态 Map 保存上下文，后续可平滑替换成 Redis / 数据库持久化。
 */
public class AgentState {

    @Getter
    private final String conversationId;
    @Getter
    private final AgentTaskType taskType;
    @Getter
    private final String originalUserMessage;
    private final Map<String, Object> artifacts = new LinkedHashMap<>();
    @Getter
    private AgentPhase currentPhase;
    @Getter
    private String latestUserMessage;
    @Getter
    private int executionRound;
    @Getter
    private boolean waitingUserInput;
    @Getter
    private boolean finished;
    @Getter
    private String finalReply;

    private AgentState(String conversationId,
                       AgentTaskType taskType,
                       AgentPhase currentPhase,
                       String originalUserMessage,
                       String latestUserMessage) {
        this.conversationId = Objects.requireNonNull(conversationId, "conversationId must not be null");
        this.taskType = Objects.requireNonNull(taskType, "taskType must not be null");
        this.currentPhase = Objects.requireNonNull(currentPhase, "currentPhase must not be null");
        this.originalUserMessage = Objects.requireNonNull(originalUserMessage, "originalUserMessage must not be null");
        this.latestUserMessage = Objects.requireNonNull(latestUserMessage, "latestUserMessage must not be null");
    }

    public static AgentState newWorkflow(String conversationId,
                                         AgentTaskType taskType,
                                         AgentPhase startPhase,
                                         String userMessage) {
        return new AgentState(conversationId, taskType, startPhase, userMessage, userMessage);
    }

    public Map<String, Object> getArtifactsView() {
        return Map.copyOf(artifacts);
    }

    public void updateLatestUserMessage(String latestUserMessage) {
        if (StringUtils.hasText(latestUserMessage)) {
            this.latestUserMessage = latestUserMessage.trim();
        }
    }

    public void incrementExecutionRound() {
        this.executionRound++;
    }

    public void moveToPhase(AgentPhase nextPhase) {
        this.currentPhase = Objects.requireNonNull(nextPhase, "nextPhase must not be null");
    }

    public void waitForUserInput() {
        this.waitingUserInput = true;
    }

    public void clearWaitingUserInput() {
        this.waitingUserInput = false;
    }

    public void mergeArtifacts(Map<String, Object> artifactsDelta) {
        if (artifactsDelta == null || artifactsDelta.isEmpty()) {
            return;
        }
        artifacts.putAll(artifactsDelta);
    }

    public void putArtifact(String key, Object value) {
        artifacts.put(key, value);
    }

    public boolean hasArtifact(String key) {
        return artifacts.containsKey(key);
    }

    @SuppressWarnings("unchecked")
    public <T> T getArtifact(String key) {
        return (T) artifacts.get(key);
    }

    public <T> T getArtifact(String key, Class<T> type) {
        Object value = artifacts.get(key);
        if (value == null || !type.isInstance(value)) {
            return null;
        }
        return type.cast(value);
    }

    public void markFinished(String finalReply) {
        this.finished = true;
        this.waitingUserInput = false;
        this.currentPhase = AgentPhase.FINISH;
        this.finalReply = sanitizeReply(finalReply, "任务已完成。");
    }

    public void markFailed(String finalReply) {
        this.finished = true;
        this.waitingUserInput = false;
        this.currentPhase = AgentPhase.FAILED;
        this.finalReply = sanitizeReply(finalReply, "任务执行失败，请稍后重试。");
    }

    private String sanitizeReply(String reply, String defaultReply) {
        return StringUtils.hasText(reply) ? reply.trim() : defaultReply;
    }
}
