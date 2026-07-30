package com.ilink.test.ilinkcheck.application.agent.orchestrator.routing;

import com.ilink.test.ilinkcheck.application.agent.orchestrator.model.AgentTaskType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

/**
 * Agent 意图路由器。
 * 这一层只负责“判断用户想触发哪条工作流”，不负责推进阶段。
 */
@Service
public class IntentRouter {

    private static final List<String> GITHUB_HINTS = List.of("github", "仓库", "repo", "readme", "开源项目", "速读");
    private static final List<String> ROADMAP_HINTS = List.of("学习路线", "路线规划", "我想学", "怎么学", "学习", "入门", "roadmap");
    private static final List<String> REVIEW_HINTS = List.of("审稿", "审一下", "博客草稿", "文档规范", "排版", "review");
    private static final List<String> DIGEST_HINTS = List.of("晨报", "日报", "订阅", "推送", "每天", "digest");

    public Optional<AgentTaskType> route(String userMessage) {
        if (!StringUtils.hasText(userMessage)) {
            return Optional.empty();
        }

        String text = userMessage.trim();
        if (containsGitHubReference(text)) {
            return Optional.of(AgentTaskType.GITHUB_REPO_DIGEST);
        }
        if (containsAny(text, ROADMAP_HINTS)) {
            return Optional.of(AgentTaskType.TECH_ROADMAP);
        }
        if (containsAny(text, REVIEW_HINTS)) {
            return Optional.of(AgentTaskType.DOC_REVIEW);
        }
        if (containsAny(text, DIGEST_HINTS)) {
            return Optional.of(AgentTaskType.DAILY_DIGEST_SUBSCRIBE);
        }

        return Optional.empty();
    }

    private boolean containsGitHubReference(String text) {
        String normalized = text.toLowerCase();
        return normalized.contains("github.com/")
                || containsAny(normalized, GITHUB_HINTS)
                || normalized.matches(".*[a-zA-Z0-9_.-]+/[a-zA-Z0-9_.-]+.*");
    }

    private boolean containsAny(String text, List<String> candidates) {
        return candidates.stream().anyMatch(text::contains);
    }
}
