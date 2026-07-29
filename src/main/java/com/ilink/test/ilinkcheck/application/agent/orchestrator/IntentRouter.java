package com.ilink.test.ilinkcheck.application.agent.orchestrator;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

/**
 * 第一版使用规则路由，先稳定把热点分析工作流接起来。
 * 后续如果要接更复杂的语义分拨器，可以在这里补 LLM fallback。
 */
@Service
public class IntentRouter {

    private static final List<String> HOTSPOT_KEYWORDS = List.of("热点", "热搜", "舆情", "选题", "全网");
    private static final List<String> HOTSPOT_ACTIONS = List.of("分析", "看看", "看下", "给我", "帮我", "想写", "写什么");

    public Optional<AgentTaskType> route(String userMessage) {
        if (!StringUtils.hasText(userMessage)) {
            return Optional.empty();
        }

        String text = userMessage.trim();
        if (containsAny(text, HOTSPOT_KEYWORDS) && containsAny(text, HOTSPOT_ACTIONS)) {
            return Optional.of(AgentTaskType.HOTSPOT_ANALYSIS);
        }

        return Optional.empty();
    }

    private boolean containsAny(String text, List<String> candidates) {
        return candidates.stream().anyMatch(text::contains);
    }
}
