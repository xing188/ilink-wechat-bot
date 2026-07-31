package com.ilink.test.ilinkcheck.application.skill;

import com.ilink.test.ilinkcheck.application.agent.dto.roadmap.DevToArticle;
import com.ilink.test.ilinkcheck.application.agent.dto.roadmap.SearchResult;
import com.ilink.test.ilinkcheck.application.agent.feature.roadmap.RoadmapComposeService;
import com.ilink.test.ilinkcheck.application.agent.spi.mcp.DevToArticleProvider;
import com.ilink.test.ilinkcheck.application.agent.spi.mcp.WebSearchProvider;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TechRoadmapSkillTest {

    private TechRoadmapSkill skill() {
        DevToArticleProvider devTo = (tag, limit) -> List.of(
                new DevToArticle("Getting Started with " + tag, "https://dev.to/x", tag, 320, "入门")
        );
        WebSearchProvider web = (query, limit) -> List.of(
                new SearchResult(query + " official docs", "https://example.com", "文档")
        );
        return new TechRoadmapSkill(devTo, web, new RoadmapComposeService());
    }

    @Test
    void idAndNameAreSet() {
        assertThat(skill().id()).isEqualTo("tech-roadmap");
        assertThat(skill().name()).isNotBlank();
        assertThat(skill().description()).isNotBlank();
    }

    @Test
    void executeExtractsTopicFromSentence() {
        String reply = skill().execute("我想学 eBPF，帮我规划学习路线");
        assertThat(reply).contains("eBPF 学习路线");
        assertThat(reply).contains("推荐资料");
    }

    @Test
    void executeAsksWhenNoTopic() {
        String reply = skill().execute("我想学");
        assertThat(reply).contains("你想学什么技术");
    }

    @Test
    void generateRoadmapUsesOriginalCasing() {
        String reply = skill().generateRoadmap("eBPF");
        assertThat(reply).contains("Getting Started with eBPF");
        assertThat(reply).doesNotContain("Getting Started with ebpf");
    }

    @Test
    void supportsChineseTopic() {
        String reply = skill().execute("我想学 微服务");
        assertThat(reply).contains("微服务 学习路线");
    }

    @Test
    void supportsBareLearningPhrase() {
        String reply = skill().execute("学习 RAG");
        assertThat(reply).contains("RAG 学习路线");
    }
}
