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

    @Test
    void shouldStripPrefixChineseFromMixedTopic() {
        // "我想学习rag" → 正则把"习"吃进主题组变成"习rag"，应剥离为 "rag" → 规范化为 "RAG"
        Optional<String> topic = AgentTextUtils.extractTopic("我想学习rag");
        assertThat(topic).contains("RAG");
    }

    @Test
    void shouldKeepPureChineseTopicUnchanged() {
        // 纯中文主题（如"微服务"）不应被剥离
        Optional<String> topic = AgentTextUtils.extractTopic("我想学微服务");
        assertThat(topic).contains("微服务");
    }

    @Test
    void shouldDetectChineseTranslationIntent() {
        assertThat(AgentTextUtils.wantsChinese("我想学rag 翻译成中文")).isTrue();
        assertThat(AgentTextUtils.wantsChinese("学习 RAG 翻译成中文版")).isTrue();
        assertThat(AgentTextUtils.wantsChinese("我想学微服务")).isFalse();
        assertThat(AgentTextUtils.wantsChinese("北京今天天气")).isFalse();
    }

    @Test
    void shouldExtractCleanTopicWhenTranslationHintPresent() {
        // "我想学rag 翻译成中文" → 剥离翻译关键词后提取 "rag" → 规范化为 "RAG"
        Optional<String> topic = AgentTextUtils.extractTopic("我想学rag 翻译成中文");
        assertThat(topic).contains("RAG");
    }

    @Test
    void shouldStripTrailingChineseFromMixedTopic() {
        // "我想学rag版" → 尾随"版"被剥离 → "rag" → "RAG"
        Optional<String> topic = AgentTextUtils.extractTopic("我想学rag版");
        assertThat(topic).contains("RAG");
    }
}
