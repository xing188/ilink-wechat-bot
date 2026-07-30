package com.ilink.test.ilinkcheck.application.agent.orchestrator.support;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AgentTextUtils 单测：重点锁定主题提取的大小写规范化（rag → RAG）。
 */
class AgentTextUtilsTest {

    @Test
    void shouldCanonicalizeLowercaseRag() {
        Optional<String> topic = AgentTextUtils.extractTopic("我想学rag");
        assertThat(topic).contains("RAG");   // 小写输入 → 规范大写 RAG
    }

    @Test
    void shouldCanonicalizeLowercaseEbpf() {
        Optional<String> topic = AgentTextUtils.extractTopic("我想学ebpf");
        assertThat(topic).contains("eBPF");  // ebpf → eBPF
    }

    @Test
    void shouldKeepAlreadyCanonicalTopic() {
        Optional<String> topic = AgentTextUtils.extractTopic("我想学RAG");
        assertThat(topic).contains("RAG");   // 已经是 RAG，保持不变
    }

    @Test
    void shouldKeepChineseTopicAsIs() {
        // 中文主题没有规范表条目，原样返回即可（原样即为规范写法）
        Optional<String> topic = AgentTextUtils.extractTopic("我想学微服务");
        assertThat(topic).contains("微服务");
    }

    @Test
    void shouldCanonicalizeMixedCaseMultiword() {
        Optional<String> topic = AgentTextUtils.extractTopic("学习 spring ai");
        assertThat(topic).contains("Spring AI");
    }
}
