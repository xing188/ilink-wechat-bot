package com.ilink.test.ilinkcheck.application.agent.orchestrator.routing;

import com.ilink.test.ilinkcheck.application.agent.orchestrator.model.AgentTaskType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

/**
 * Agent 意图路由器
 * 职责：判断用户消息是否需要启动指定Agent工作流
 * 规则：自上而下顺序匹配，先命中优先；无法匹配返回Optional.empty，交由普通对话链路处理
 * 【注意】当前版本：基于关键词规则匹配；后续可平滑升级为LLM语义路由
 */
@Slf4j
@Service
public class IntentRouter {

    // Github项目速读触发关键词
    private static final List<String> GITHUB_HINTS = List.of("github", "仓库", "repo", "readme", "开源项目", "速读");
    // 学习路线任务触发关键词
    private static final List<String> ROADMAP_HINTS = List.of("学习路线", "路线规划", "我想学", "怎么学", "入门", "roadmap");
    // 博文审稿触发关键词
    private static final List<String> REVIEW_HINTS = List.of("审稿", "审一下", "博客草稿", "文档规范", "排版", "review");
    // 技术晨报订阅关键词
    private static final List<String> DIGEST_HINTS = List.of("晨报", "日报", "订阅", "推送", "每天", "digest");

    public Optional<AgentTaskType> route(String userMessage) {
        if (!StringUtils.hasText(userMessage)) {
            return Optional.empty();
        }
        String text = userMessage.trim();

        Optional<AgentTaskType> result = doRoute(text);
        log.info("IntentRouter route result, message={}, matchTask={}", text, result.map(AgentTaskType::name).orElse("NONE"));
        return result;
    }

    private Optional<AgentTaskType> doRoute(String text) {
        // 优先级1：Github仓库识别
        if (containsGitHubReference(text)) {
            return Optional.of(AgentTaskType.GITHUB_REPO_DIGEST);
        }
        // 优先级2：学习路线
        if (containsAny(text, ROADMAP_HINTS)) {
            return Optional.of(AgentTaskType.TECH_ROADMAP);
        }
        // 优先级3：文档审稿
        if (containsAny(text, REVIEW_HINTS)) {
            return Optional.of(AgentTaskType.DOC_REVIEW);
        }
        // 优先级4：晨报订阅
        if (containsAny(text, DIGEST_HINTS)) {
            return Optional.of(AgentTaskType.DAILY_DIGEST_SUBSCRIBE);
        }

        return Optional.empty();
    }

    /**
     * 判断是否为Github相关请求
     * 匹配规则：github链接 || 关键词；弱化粗暴xxx/yyy正则，防止大量误匹配
     */
    private boolean containsGitHubReference(String text) {
        String normalized = text.toLowerCase();
        // 优先精准匹配github链接，移除宽泛的xxx/yyy正则避免误命中
        boolean hasGithubUrl = normalized.contains("github.com/");
        boolean hasKeyword = containsAny(normalized, GITHUB_HINTS);
        return hasGithubUrl || hasKeyword;
    }

    /**
     * 通用关键词包含匹配
     */
    private boolean containsAny(String text, List<String> candidates) {
        return candidates.stream().anyMatch(text::contains);
    }
}