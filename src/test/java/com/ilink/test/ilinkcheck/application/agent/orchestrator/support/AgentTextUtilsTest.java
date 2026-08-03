package com.ilink.test.ilinkcheck.application.agent.orchestrator.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentTextUtilsTest {

    @Test
    void wantsDetailedShouldMatchPositiveKeywords() {
        assertThat(AgentTextUtils.wantsDetailed("详细")).isTrue();
        assertThat(AgentTextUtils.wantsDetailed("要完整版")).isTrue();
        assertThat(AgentTextUtils.wantsDetailed("展开看看")).isTrue();
        assertThat(AgentTextUtils.wantsDetailed("可以的，发完整版吧")).isTrue();
        assertThat(AgentTextUtils.wantsDetailed("继续")).isTrue();
    }

    @Test
    void wantsDetailedShouldRejectNegationEvenWhenPositiveKeywordPresent() {
        assertThat(AgentTextUtils.wantsDetailed("不用了")).isFalse();
        assertThat(AgentTextUtils.wantsDetailed("不要")).isFalse();
        assertThat(AgentTextUtils.wantsDetailed("算了，不用")).isFalse();
        assertThat(AgentTextUtils.wantsDetailed("暂时不需要")).isFalse();
    }

    @Test
    void wantsDetailedShouldBeFalseForIrrelevantReply() {
        assertThat(AgentTextUtils.wantsDetailed("谢谢")).isFalse();
        assertThat(AgentTextUtils.wantsDetailed("好的")).isTrue();
        assertThat(AgentTextUtils.wantsDetailed("")).isFalse();
        assertThat(AgentTextUtils.wantsDetailed(null)).isFalse();
    }

    @Test
    void wantsChineseShouldDetectTranslationIntent() {
        assertThat(AgentTextUtils.wantsChinese("翻译成中文")).isTrue();
        assertThat(AgentTextUtils.wantsChinese("我要中文版")).isTrue();
        assertThat(AgentTextUtils.wantsChinese("我想学 RAG")).isFalse();
    }

    @Test
    void extractTopicShouldIgnoreTranslationHints() {
        assertThat(AgentTextUtils.extractTopic("我想学 RAG 翻译成中文"))
                .contains("RAG");
        assertThat(AgentTextUtils.extractTopic("学习 eBPF"))
                .contains("eBPF");
    }

    @Test
    void wantsVideoShouldMatchVideoKeywords() {
        assertThat(AgentTextUtils.wantsVideo("视频")).isTrue();
        assertThat(AgentTextUtils.wantsVideo("网课")).isTrue();
        assertThat(AgentTextUtils.wantsVideo("B站教程")).isTrue();
        assertThat(AgentTextUtils.wantsVideo("bilibili 上的课")).isTrue();
        assertThat(AgentTextUtils.wantsVideo("可以的，发视频吧")).isTrue();
    }

    @Test
    void wantsVideoShouldRejectNegationEvenWhenVideoKeywordPresent() {
        assertThat(AgentTextUtils.wantsVideo("不用了")).isFalse();
        assertThat(AgentTextUtils.wantsVideo("不要视频")).isFalse();
        assertThat(AgentTextUtils.wantsVideo("暂时不需要网课")).isFalse();
    }

    @Test
    void wantsVideoShouldBeFalseForIrrelevantReply() {
        assertThat(AgentTextUtils.wantsVideo("谢谢")).isFalse();
        assertThat(AgentTextUtils.wantsVideo("")).isFalse();
        assertThat(AgentTextUtils.wantsVideo(null)).isFalse();
    }

    @Test
    void wantsMindmapShouldMatchMindmapKeywords() {
        assertThat(AgentTextUtils.wantsMindmap("思维导图")).isTrue();
        assertThat(AgentTextUtils.wantsMindmap("脑图")).isTrue();
        assertThat(AgentTextUtils.wantsMindmap("给我个大纲")).isTrue();
        assertThat(AgentTextUtils.wantsMindmap("mindmap")).isTrue();
        assertThat(AgentTextUtils.wantsMindmap("想要 mind map")).isTrue();
    }

    @Test
    void wantsMindmapShouldRejectNegationEvenWhenMindmapKeywordPresent() {
        assertThat(AgentTextUtils.wantsMindmap("不用了")).isFalse();
        assertThat(AgentTextUtils.wantsMindmap("不要思维导图")).isFalse();
        assertThat(AgentTextUtils.wantsMindmap("暂时不需要脑图")).isFalse();
    }

    @Test
    void wantsMindmapShouldBeFalseForIrrelevantReply() {
        assertThat(AgentTextUtils.wantsMindmap("谢谢")).isFalse();
        assertThat(AgentTextUtils.wantsMindmap("")).isFalse();
        assertThat(AgentTextUtils.wantsMindmap(null)).isFalse();
    }
}
