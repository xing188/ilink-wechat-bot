package com.ilink.test.ilinkcheck.application.agent;

import com.ilink.test.ilinkcheck.application.tool.ToolRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class AgentService {

    private static final int MAX_ITERATIONS = 10;

    private static final String AGENT_PROMPT = """
            你是一个通过逐步推理和调用工具来完成任务的智能助手。

            【工作流程】
            1. 思考：分析用户需求，决定需要哪些信息
            2. 调用工具：使用合适的工具获取信息
            3. 观察：查看工具返回的结果
            4. 重复：如果信息不足，继续思考和调用
            5. 回答：收集到足够信息后，给出完整的最终回答

            【规则】
            - 每次只调用一个工具，等待返回结果后再决定下一步
            - 不要凭空编造信息，只使用工具返回的真实数据
            - 如果多个工具调用后仍无法完成任务，诚实告知用户
            - 最终回答必须用中文，完整且有条理
            """;

    private final ChatClient chatClient;
    private final ToolRegistry toolRegistry;

    public AgentService(ChatClient conversationChatClient,
                        ToolRegistry toolRegistry) {
        this.chatClient = conversationChatClient;
        this.toolRegistry = toolRegistry;
    }

    public String execute(String userMessage) {
        log.info("🤖 Agent 开始执行: {}", userMessage);

        String result = chatClient.prompt()
                .system(AGENT_PROMPT)
                .user(userMessage)
                .tools(toolRegistry.toolObjects())
                .call()
                .content();

        log.info("🤖 Agent 执行完毕");
        return result;
    }
}
