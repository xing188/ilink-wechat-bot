package com.ilink.test.ilinkcheck.application.agent.orchestrator.routing;

import com.ilink.test.ilinkcheck.application.agent.orchestrator.model.AgentTaskType;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.routing.IntentRouter.LlmVerdict;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * IntentRouter 单元测试。
 * 验证 LLM 语义路由与关键词规则兜底的混合策略。
 */
class IntentRouterTest {

    private ChatClient mockChatClient(String content) {
        ChatClient cc = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec spec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec call = mock(ChatClient.CallResponseSpec.class);
        when(cc.prompt()).thenReturn(spec);
        when(spec.system(anyString())).thenReturn(spec);
        when(spec.user(anyString())).thenReturn(spec);
        when(spec.call()).thenReturn(call);
        when(call.content()).thenReturn(content);
        return cc;
    }

    private IntentRouter llmRouter(ChatClient cc) {
        IntentRouter router = new IntentRouter();
        router.setRoutingChatClient(cc);
        router.setLlmEnabled(true);
        return router;
    }

    // ── 高确定性短路 ─────────────────────────────────────────────

    @Test
    void githubUrlShortCircuitsWithoutLlm() {
        IntentRouter router = new IntentRouter(); // 无 client，也能命中
        assertThat(router.route("帮我看看 https://github.com/langgraph-ai/langgraph 是干嘛的"))
                .contains(AgentTaskType.GITHUB_REPO_DIGEST);
    }

    // ── LLM 语义路由 ─────────────────────────────────────────────

    @Test
    void llmTaskWins() {
        IntentRouter router = llmRouter(mockChatClient("{\"task\": \"TECH_ROADMAP\"}"));
        assertThat(router.route("我想用 RAG 做个项目，帮我理一下思路"))
                .contains(AgentTaskType.TECH_ROADMAP);
    }

    @Test
    void llmNoneShortCircuitsEvenWhenKeywordMatches() {
        IntentRouter router = llmRouter(mockChatClient("{\"task\": \"NONE\"}"));
        // 关键词"学习"本会命中 TECH_ROADMAP，但 LLM 判定 NONE 优先
        assertThat(router.route("我今天去学习了")).isEmpty();
    }

    @Test
    void longTextShortCircuitsEvenWhenLlmSaysNone() {
        IntentRouter router = llmRouter(mockChatClient("{\"task\": \"NONE\"}"));
        // 用户粘贴长文章（含换行/代码块）属于高置信信号，不应被 LLM NONE 短路，
        // 否则审稿流程无法触发、监控台也看不到事件。
        String longDraft = "这是我写的 Redis延迟排查草稿。\n\n```\nINFO commandstats\n```\n\n这篇文章主要讨论 redis延迟 的排查路径。";
        assertThat(router.route(longDraft)).contains(AgentTaskType.DOC_REVIEW);
    }

    @Test
    void llmEmptyContentFallsBackToRule() {
        IntentRouter router = llmRouter(mockChatClient(null));
        assertThat(router.route("我想学 eBPF，帮我规划学习路线"))
                .contains(AgentTaskType.TECH_ROADMAP);
    }

    @Test
    void llmFailureFallsBackToRule() {
        ChatClient cc = mockChatClient("{\"task\": \"TECH_ROADMAP\"}");
        when(cc.prompt().call().content()).thenThrow(new RuntimeException("api down"));
        IntentRouter router = llmRouter(cc);
        assertThat(router.route("我想学 eBPF，帮我规划学习路线"))
                .contains(AgentTaskType.TECH_ROADMAP);
    }

    @Test
    void llmTimeoutFallsBackToRule() {
        ChatClient cc = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec spec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec call = mock(ChatClient.CallResponseSpec.class);
        when(cc.prompt()).thenReturn(spec);
        when(spec.system(anyString())).thenReturn(spec);
        when(spec.user(anyString())).thenReturn(spec);
        when(spec.call()).thenReturn(call);
        when(call.content()).thenAnswer(invocation -> {
            Thread.sleep(300);
            return "{\"task\":\"TECH_ROADMAP\"}";
        });
        IntentRouter router = llmRouter(cc);
        router.setLlmTimeoutMs(50);
        assertThat(router.route("我想学 eBPF，帮我规划学习路线"))
                .contains(AgentTaskType.TECH_ROADMAP);
    }

    // ── LLM 输出解析 ─────────────────────────────────────────────

    @Test
    void parseJsonFence() {
        IntentRouter router = new IntentRouter();
        assertThat(router.parseLlmResponse("```json\n{\"task\": \"DAILY_DIGEST_SUBSCRIBE\"}\n```"))
                .isEqualTo(LlmVerdict.task(AgentTaskType.DAILY_DIGEST_SUBSCRIBE));
    }

    @Test
    void parseBareToken() {
        IntentRouter router = new IntentRouter();
        assertThat(router.parseLlmResponse("DOC_REVIEW"))
                .isEqualTo(LlmVerdict.task(AgentTaskType.DOC_REVIEW));
    }

    @Test
    void parseNone() {
        IntentRouter router = new IntentRouter();
        assertThat(router.parseLlmResponse("NONE").decision())
                .isEqualTo(LlmVerdict.LlmDecision.NONE);
    }

    @Test
    void parseUnknownTokenDegrades() {
        IntentRouter router = new IntentRouter();
        assertThat(router.parseLlmResponse("{\"task\": \"UNKNOWN_TASK\"}").decision())
                .isEqualTo(LlmVerdict.LlmDecision.DEGRADED);
    }

    @Test
    void parseBlankDegrades() {
        IntentRouter router = new IntentRouter();
        assertThat(router.parseLlmResponse("   ").decision())
                .isEqualTo(LlmVerdict.LlmDecision.DEGRADED);
    }

    // ── 无 LLM 时关键词规则（旧行为保持） ─────────────────────────

    @Test
    void ruleStillWorksWithoutLlm() {
        IntentRouter router = new IntentRouter();
        assertThat(router.route("帮我审一下这篇博客草稿"))
                .contains(AgentTaskType.DOC_REVIEW);
        assertThat(router.route("每天 08:30 推送技术晨报"))
                .contains(AgentTaskType.DAILY_DIGEST_SUBSCRIBE);
        assertThat(router.route("北京今天天气怎么样")).isEmpty();
    }
}
