package com.ilink.test.ilinkcheck.application.agent.orchestrator.model;

import lombok.Getter;
import org.springframework.util.StringUtils;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Agent 工作流运行时状态实体
 * 承载单次对话内Agent整个执行生命周期的全部上下文信息
 * 职责：保存阶段、执行轮次、用户消息、过程产出物artifacts、暂停等待用户输入标记
 * 设计规划：首期内存存储；后续可无缝迁移至Redis/数据库持久化，实现会话断点续跑
 */
public class AgentState {

    // 会话唯一标识，绑定一条用户对话
    @Getter
    private final String conversationId;
    // 任务类型：审稿、资料检索、代码分析等，区分不同Agent业务流程
    @Getter
    private final AgentTaskType taskType;
    // 用户初始发起任务的原始提问
    @Getter
    private final String originalUserMessage;

    /**
     * Artifacts：工作流执行过程产出物
     * 存储工具返回结果、RAG检索内容、中间计划、临时数据等跨阶段共享数据
     */
    private final Map<String, Object> artifacts = new LinkedHashMap<>();

    @Getter
    private AgentPhase currentPhase;
    @Getter
    private String latestUserMessage;
    // Agent循环执行轮次，统计ReAct多轮调用次数
    @Getter
    private int executionRound;
    // 是否主动暂停，等待用户补充输入
    @Getter
    private boolean waitingUserInput;
    // 用户回复后需要恢复执行的阶段
    @Getter
    private AgentPhase resumePhaseAfterUserInput;
    @Getter
    private boolean finished;
    // 最终输出给用户的回复
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

    /**
     * 创建全新Agent工作流状态
     */
    public static AgentState newWorkflow(String conversationId,
                                         AgentTaskType taskType,
                                         AgentPhase startPhase,
                                         String userMessage) {
        return new AgentState(conversationId, taskType, startPhase, userMessage, userMessage);
    }

    /**
     * 返回不可变视图，防止外部直接修改artifacts
     */
    public Map<String, Object> getArtifactsView() {
        return Map.copyOf(artifacts);
    }

    public void updateLatestUserMessage(String latestUserMessage) {
        if (StringUtils.hasText(latestUserMessage)) {
            this.latestUserMessage = latestUserMessage.trim();
        }
    }

    /**
     * Agent执行轮次+1，用于限制最大循环次数，防止无限ReAct
     */
    public void incrementExecutionRound() {
        this.executionRound++;
    }

    /**
     * 切换至下一执行阶段
     */
    public void moveToPhase(AgentPhase nextPhase) {
        this.currentPhase = Objects.requireNonNull(nextPhase, "nextPhase must not be null");
        // 如果不是等待输入阶段，清空恢复点位
        if (nextPhase != AgentPhase.WAIT_USER_INPUT) {
            this.resumePhaseAfterUserInput = null;
        }
    }

    /**
     * 暂停工作流，等待用户输入，并标记恢复后要进入的阶段
     */
    public void waitForUserInput(AgentPhase resumePhase) {
        this.waitingUserInput = true;
        this.resumePhaseAfterUserInput = Objects.requireNonNull(resumePhase, "resumePhase must not be null");
        this.currentPhase = AgentPhase.WAIT_USER_INPUT;
    }

    /**
     * 用户完成输入，从暂停位置恢复执行
     */
    public void resumeFromWaitingUserInput() {
        if (resumePhaseAfterUserInput == null) {
            throw new IllegalStateException("resumePhaseAfterUserInput must not be null when resuming workflow");
        }
        this.currentPhase = resumePhaseAfterUserInput;
        this.resumePhaseAfterUserInput = null;
    }

    public void clearWaitingUserInput() {
        this.waitingUserInput = false;
    }

    /**
     * 批量写入过程产出物
     */
    public void mergeArtifacts(Map<String, Object> artifactsDelta) {
        if (artifactsDelta == null || artifactsDelta.isEmpty()) {
            return;
        }
        artifacts.putAll(artifactsDelta);
    }

    /**
     * 单个存入产出物
     */
    public void putArtifact(String key, Object value) {
        artifacts.put(key, value);
    }

    public boolean hasArtifact(String key) {
        return artifacts.containsKey(key);
    }

    /**
     * 不校验类型直接获取，需要使用者自行保证类型安全
     */
    @SuppressWarnings("unchecked")
    public <T> T getArtifact(String key) {
        return (T) artifacts.get(key);
    }

    /**
     * 带类型校验安全读取产出物
     */
    public <T> T getArtifact(String key, Class<T> type) {
        Object value = artifacts.get(key);
        if (value == null || !type.isInstance(value)) {
            return null;
        }
        return type.cast(value);
    }

    /**
     * 标记任务正常结束
     */
    public void markFinished(String finalReply) {
        this.finished = true;
        this.waitingUserInput = false;
        this.resumePhaseAfterUserInput = null;
        this.currentPhase = AgentPhase.FINISH;
        this.finalReply = sanitizeReply(finalReply, "任务已完成。");
    }

    /**
     * 标记任务异常失败终止
     */
    public void markFailed(String finalReply) {
        this.finished = true;
        this.waitingUserInput = false;
        this.resumePhaseAfterUserInput = null;
        this.currentPhase = AgentPhase.FAILED;
        this.finalReply = sanitizeReply(finalReply, "任务执行失败，请稍后重试。");
    }

    /**
     * 回复兜底处理，空文本填充默认文案
     */
    private String sanitizeReply(String reply, String defaultReply) {
        return StringUtils.hasText(reply) ? reply.trim() : defaultReply;
    }
}