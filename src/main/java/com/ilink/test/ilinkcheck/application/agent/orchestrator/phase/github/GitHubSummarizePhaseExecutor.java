package com.ilink.test.ilinkcheck.application.agent.orchestrator.phase.github;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ilink.test.ilinkcheck.application.agent.dto.github.RepoInfo;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * GitHub 速读第二阶段。
 * 负责把仓库原始资料通过大模型提炼成中文摘要回复。
 */
@Slf4j
@Service
public class GitHubSummarizePhaseExecutor implements PhaseExecutor {

    private static final String MSG_MISS_REPO_DATA = "仓库原始资料缺失，本次速读未能继续。";

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GitHubSummarizePhaseExecutor(
            @Qualifier("plannerChatClient") ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public boolean supports(AgentTaskType taskType, AgentPhase phase) {
        return taskType == AgentTaskType.GITHUB_REPO_DIGEST && phase == AgentPhase.GITHUB_SUMMARIZE;
    }

    @Override
    public PhaseExecutionResult execute(AgentState state) {
        String conversationId = state.getConversationId();
        RepoInfo repoInfo = state.getArtifact(AgentArtifacts.REPO_INFO, RepoInfo.class);

        if (repoInfo == null) {
            log.warn("conversationId={}, phase=GITHUB_SUMMARIZE, artifact REPO_INFO missing", conversationId);
            return PhaseExecutionResult.finish(MSG_MISS_REPO_DATA, Map.of());
        }

        log.info("conversationId={}, phase=GITHUB_SUMMARIZE, start render summary, repo={}/{}",
                conversationId, repoInfo.owner(), repoInfo.name());

        String repoFullName = repoInfo.owner() + "/" + repoInfo.name();

        // 使用大模型提炼一句话定位（中文）
        String oneLiner = summarizeOneLiner(repoInfo.description(), repoFullName);

        // 使用大模型提炼 README 摘要（JSON 结构化，各字段可单独取用）
        String readmeRaw = state.getArtifact(AgentArtifacts.README_RAW, String.class);
        ReadmeSections sections = summarizeReadme(readmeRaw, repoInfo.readmeSummary(), repoFullName);

        // 组装完整回复
        String readmeDisplay = sections.toDisplayText();

        String reply = """
                GitHub 仓库速读：%s/%s

                一句话定位：
                %s

                主要语言：%s
                Star：%d
                Fork：%d
                技术栈：%s

                README 提炼：
                %s

                如果你愿意，我下一步可以继续帮你拆 README 结构或快速上手路径。
                """.formatted(
                repoInfo.owner(),
                repoInfo.name(),
                oneLiner,
                defaultText(repoInfo.language(), "未知"),
                repoInfo.stars(),
                repoInfo.forks(),
                defaultText(repoInfo.techStack(), "暂无"),
                readmeDisplay
        ).trim();

        // 将结构化 JSON 存入工件，供后续单独取用各字段
        Map<String, Object> result = new HashMap<>();
        result.put(AgentArtifacts.README_SUMMARY_JSON, sections);
        result.put(AgentArtifacts.REQUEST_REPOSITORY, repoFullName);
        result.put(AgentArtifacts.REPO_INFO, repoInfo);

        // 速读摘要输出后暂停，询问用户是否需要继续分析核心目录架构
        String askDirectory = reply + "\n\n👉 需要我继续分析它的核心目录架构吗？回复「好」查看，回复「不用」跳过。";
        log.info("conversationId={}, phase=GITHUB_SUMMARIZE, ask for directory analysis", conversationId);
        return PhaseExecutionResult.waitForUser(
                AgentPhase.GITHUB_DIRECTORY_ANALYZE,
                askDirectory,
                result
        );
    }

    /**
     * 调用大模型将仓库描述提炼为中文一句话定位
     */
    private String summarizeOneLiner(String description, String repoFullName) {
        if (!StringUtils.hasText(description)) {
            return "暂无描述，建议阅读 README 了解更多。";
        }
        try {
            String prompt = """
                    你是一位技术项目分析师。请根据以下 GitHub 仓库的英文描述，用一句简洁的中文概括这个项目的核心定位和解决的问题。
                    要求：
                    1. 只输出一句话，不要换行，不要编号
                    2. 语言精练，20-50字为宜
                    3. 突出项目的核心价值和适用场景

                    仓库：%s
                    英文描述：%s
                    """.formatted(repoFullName, description);

            String result = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            return StringUtils.hasText(result) ? result.trim() : description;
        } catch (Exception e) {
            log.warn("LLM one-liner summarization failed for repo={}", repoFullName, e);
            return description;
        }
    }

    /**
     * 调用大模型将 README 内容提炼为结构化 JSON 摘要，各字段可单独取用。
     * 返回 ReadmeSections 对象，包含 intro / coreFeatures / architecture / quickStart。
     */
    private ReadmeSections summarizeReadme(String readmeRaw, String readmeSummary, String repoFullName) {
        String sourceContent = StringUtils.hasText(readmeRaw) ? readmeRaw : readmeSummary;
        if (!StringUtils.hasText(sourceContent)) {
            return ReadmeSections.empty();
        }
        try {
            String prompt = """
                    你是一位技术项目分析师。请阅读以下 GitHub 仓库的 README 内容，用中文提炼一份结构化的项目摘要。
                    要求：
                    1. 使用中文输出，保留关键技术术语的英文原文
                    2. 每个字段用简洁的段落描述，不要使用省略号，不要截断内容
                    3. 内容适中，每个字段2-4句话
                    4. 如果 README 内容不足以支撑某个字段，该字段填 null
                    5. 只返回 JSON，不要其他内容

                    返回格式：
                    {
                      "intro": "项目简介，概括项目是什么、解决什么问题",
                      "coreFeatures": "核心功能，列出主要能力",
                      "architecture": "技术架构，描述整体设计和模块划分，无则填null",
                      "quickStart": "快速上手，说明如何开始使用，无则填null"
                    }

                    仓库：%s
                    README 内容：
                    %s
                    """.formatted(repoFullName, sourceContent);

            String result = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            if (!StringUtils.hasText(result)) {
                return ReadmeSections.fallback(sourceContent);
            }

            return parseReadmeJson(result.trim(), sourceContent);
        } catch (Exception e) {
            log.warn("LLM README summarization failed for repo={}", repoFullName, e);
            String fallback = StringUtils.hasText(readmeSummary) ? readmeSummary : sourceContent;
            return ReadmeSections.fallback(fallback);
        }
    }

    /**
     * 解析 LLM 返回的 JSON，容错处理 markdown 代码块包裹的情况
     */
    private ReadmeSections parseReadmeJson(String raw, String sourceContent) {
        try {
            // 容错：LLM 可能用 ```json ... ``` 包裹
            String json = raw;
            if (json.contains("```")) {
                json = json.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
            }

            JsonNode node = objectMapper.readTree(json);
            return new ReadmeSections(
                    getTextOrNull(node, "intro"),
                    getTextOrNull(node, "coreFeatures"),
                    getTextOrNull(node, "architecture"),
                    getTextOrNull(node, "quickStart")
            );
        } catch (Exception e) {
            log.warn("Failed to parse LLM JSON response, using raw text as fallback", e);
            return ReadmeSections.fallback(sourceContent);
        }
    }

    private String getTextOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return StringUtils.hasText(text) ? text.trim() : null;
    }

    private String defaultText(String text, String fallback) {
        return text == null || text.isBlank() ? fallback : text.trim();
    }

    /**
     * README 结构化摘要，各字段可独立取用。
     */
    public static class ReadmeSections {
        private final String intro;
        private final String coreFeatures;
        private final String architecture;
        private final String quickStart;

        public ReadmeSections(String intro, String coreFeatures, String architecture, String quickStart) {
            this.intro = intro;
            this.coreFeatures = coreFeatures;
            this.architecture = architecture;
            this.quickStart = quickStart;
        }

        public static ReadmeSections empty() {
            return new ReadmeSections(null, null, null, null);
        }

        public static ReadmeSections fallback(String text) {
            return new ReadmeSections(text, null, null, null);
        }

        public String getIntro() { return intro; }
        public String getCoreFeatures() { return coreFeatures; }
        public String getArchitecture() { return architecture; }
        public String getQuickStart() { return quickStart; }

        /**
         * 组装为用户可读的展示文本
         */
        public String toDisplayText() {
            StringBuilder sb = new StringBuilder();
            if (StringUtils.hasText(intro)) {
                sb.append("项目简介\n").append(intro);
            }
            if (StringUtils.hasText(coreFeatures)) {
                if (sb.length() > 0) sb.append("\n\n");
                sb.append("核心功能\n").append(coreFeatures);
            }
            if (StringUtils.hasText(architecture)) {
                if (sb.length() > 0) sb.append("\n\n");
                sb.append("技术架构\n").append(architecture);
            }
            if (StringUtils.hasText(quickStart)) {
                if (sb.length() > 0) sb.append("\n\n");
                sb.append("快速上手\n").append(quickStart);
            }
            return sb.length() > 0 ? sb.toString() : "暂无摘要";
        }
    }
}
