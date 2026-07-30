package com.ilink.test.ilinkcheck.application.agent.feature.roadmap;

import com.ilink.test.ilinkcheck.application.agent.dto.roadmap.DevToArticle;
import com.ilink.test.ilinkcheck.application.agent.dto.roadmap.RoadmapPlan;
import com.ilink.test.ilinkcheck.application.agent.dto.roadmap.RoadmapStep;
import com.ilink.test.ilinkcheck.application.agent.dto.roadmap.SearchResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RoadmapComposeServiceTest {

    private final RoadmapComposeService service = new RoadmapComposeService();

    @Test
    void composeProducesStructuredPlanWithTopic() {
        RoadmapPlan plan = service.compose("eBPF", List.of(), List.of());
        assertThat(plan.topic()).isEqualTo("eBPF");
        assertThat(plan.prerequisites()).isNotEmpty();
        assertThat(plan.coreConcepts()).anyMatch(c -> c.title().contains("eBPF"));
        assertThat(plan.projectIdeas()).isNotEmpty();
    }

    @Test
    void renderIncludesAllSectionsAndArticles() {
        List<DevToArticle> articles = List.of(
                new DevToArticle("Getting Started with eBPF", "https://dev.to/x", "ebpf", 320, "入门文章")
        );
        List<SearchResult> refs = List.of(
                new SearchResult("eBPF official docs", "https://example.com", "官方文档")
        );
        String reply = service.render(service.compose("eBPF", articles, refs), refs);

        assertThat(reply).contains("eBPF 学习路线");
        assertThat(reply).contains("第一阶段：预备知识");
        assertThat(reply).contains("第二阶段：核心概念");
        assertThat(reply).contains("第三阶段：实战建议");
        assertThat(reply).contains("推荐资料");
        assertThat(reply).contains("Getting Started with eBPF");
        assertThat(reply).contains("👍 320");
        assertThat(reply).contains("（来源：Dev.to）");
        assertThat(reply).contains("补充参考");
        assertThat(reply).contains("eBPF official docs");
        assertThat(reply).contains("（来源：Example）");
    }

    @Test
    void renderHandlesEmptyArticlesAndReferencesGracefully() {
        String reply = service.buildReply("RAG", null, null);
        assertThat(reply).contains("暂无 Dev.to 推荐文章");
        assertThat(reply).contains("暂无补充检索结果");
        assertThat(reply).contains("RAG 学习路线");
    }

    @Test
    void preservesOriginalTopicCasing() {
        // 主题大小写必须保留（之前被 toLowerCase 破坏，导致 eBPF 显示成 ebpf）
        String reply = service.buildReply("eBPF", List.of(), List.of());
        assertThat(reply).contains("eBPF 学习路线");
        assertThat(reply).doesNotContain("ebpf 学习路线");
    }

    @Test
    void eachStepHasDescriptionField() {
        // 验证 RoadmapStep 的 description 字段已被填充并在渲染中展示
        RoadmapPlan plan = service.compose("eBPF", List.of(), List.of());

        // 每个阶段至少有一个步骤带非空 description
        assertThat(plan.prerequisites()).anyMatch(s -> s.description() != null && !s.description().isBlank());
        assertThat(plan.coreConcepts()).anyMatch(s -> s.description() != null && !s.description().isBlank());
        assertThat(plan.projectIdeas()).anyMatch(s -> s.description() != null && !s.description().isBlank());

        // 渲染输出中包含"标题：说明"格式（冒号分隔）
        String reply = service.render(plan, List.of());
        assertThat(reply).contains("：");  // 标题与说明之间有冒号
        assertThat(reply).contains("掌握变量");  // 预备知识第一条的详细说明关键词
    }

    @Test
    void renderFallsBackToTitleOnlyWhenDescriptionBlank() {
        // description 为空时只显示标题，不显示冒号
        List<RoadmapStep> stepsWithEmptyDesc = List.of(
                RoadmapStep.ofTitle("只有标题"),
                RoadmapStep.of("有标题", "  ")   // blank
        );
        RoadmapPlan plan = new RoadmapPlan("test", stepsWithEmptyDesc, List.of(), List.of(), List.of());
        String reply = service.render(plan, List.of());
        assertThat(reply).contains("- 只有标题");
        assertThat(reply).contains("- 有标题");
    }

    @Test
    void articleRenderedAsClickableMarkdownLink() {
        // 推荐资料应渲染为 [标题](url) 可点击链接
        List<DevToArticle> articles = List.of(
                new DevToArticle("Getting Started with eBPF", "https://dev.to/real-article", "ebpf", 320, "入门")
        );
        String reply = service.render(service.compose("eBPF", articles, List.of()), List.of());
        assertThat(reply).contains("[Getting Started with eBPF](https://dev.to/real-article)");
    }

    @Test
    void articleFallsBackToPlainTitleWhenUrlBlank() {
        // url 为空时退化为纯标题，不生成无效链接 [标题]()，来源标注为未知来源
        List<DevToArticle> articles = List.of(
                new DevToArticle("No Url Article", "", "x", 10, "desc")
        );
        String reply = service.render(service.compose("eBPF", articles, List.of()), List.of());
        assertThat(reply).contains("- No Url Article（来源：未知来源）");
        assertThat(reply).doesNotContain("[No Url Article]()");
    }

    @Test
    void sourceDerivedFromUrlDomain() {
        // 来源说明应根据链接域名自动推断
        assertThat(service.deriveSource("https://github.com/owner/repo")).isEqualTo("GitHub");
        assertThat(service.deriveSource("https://dev.to/tag/article")).isEqualTo("Dev.to");
        assertThat(service.deriveSource("https://stackoverflow.com/questions/1")).isEqualTo("Stack Overflow");
        assertThat(service.deriveSource("https://docs.spring.io/spring/docs")).isEqualTo("Spring 官方文档");
        // 未知域名回退为去掉前缀子域后的可读名称
        assertThat(service.deriveSource("https://example.com/docs")).isEqualTo("Example");
        // 链接为空时回退为未知来源
        assertThat(service.deriveSource("")).isEqualTo("未知来源");
        assertThat(service.deriveSource(null)).isEqualTo("未知来源");
    }

    @Test
    void referenceRenderedAsClickableMarkdownLink() {
        // 补充参考也应渲染为 [标题](url) 可点击链接
        List<SearchResult> refs = List.of(
                new SearchResult("eBPF official docs", "https://example.com/docs", "官方文档")
        );
        String reply = service.render(service.compose("eBPF", List.of(), refs), refs);
        assertThat(reply).contains("[eBPF official docs](https://example.com/docs)");
    }
}
