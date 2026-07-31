package com.ilink.test.ilinkcheck.application.agent.feature.roadmap;

import com.ilink.test.ilinkcheck.application.agent.dto.roadmap.DevToArticle;
import com.ilink.test.ilinkcheck.application.agent.dto.roadmap.RoadmapPlan;
import com.ilink.test.ilinkcheck.application.agent.dto.roadmap.RoadmapStep;
import com.ilink.test.ilinkcheck.application.agent.dto.roadmap.SearchResult;
import com.ilink.test.ilinkcheck.application.agent.feature.roadmap.RoadmapComposeService.RoadmapPlanDto;
import com.ilink.test.ilinkcheck.application.tool.categories.translation.TranslationService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

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

    @Test
    void translatesArticleAndReferenceWhenTranslateFlagSet() {
        // 当 translate=true 且注入翻译服务时，英文资料应被批量翻译成中文
        TranslationService mockTranslator = new TranslationService() {
            @Override public String translate(String text, String src, String tgt) { return "【译】" + text; }
            @Override public List<String> batchTranslate(List<String> texts, String src, String tgt) {
                return texts.stream().map(t -> "【译】" + t).toList();
            }
        };
        RoadmapComposeService translatingService = new RoadmapComposeService();
        translatingService.setTranslationService(mockTranslator);

        List<DevToArticle> articles = List.of(
                new DevToArticle("Getting Started with eBPF", "https://dev.to/x", "ebpf", 320, "An intro article")
        );
        List<SearchResult> refs = List.of(
                new SearchResult("eBPF official docs", "https://example.com/docs", "Official documentation")
        );

        String reply = translatingService.render(
                translatingService.compose("eBPF", articles, refs), refs, true);

        assertThat(reply).contains("【译】Getting Started with eBPF");
        assertThat(reply).contains("【译】An intro article");
        assertThat(reply).contains("【译】eBPF official docs");
        assertThat(reply).contains("【译】Official documentation");
        // 链接 url 不应被翻译
        assertThat(reply).contains("(https://dev.to/x)");
    }

    @Test
    void keepsOriginalWhenTranslateFlagFalseOrNoTranslator() {
        // translate=false：即使注入了翻译服务，资料也保持原文（不调用 batchTranslate）
        TranslationService mockTranslator = new TranslationService() {
            @Override public String translate(String text, String src, String tgt) { return "【译】" + text; }
            @Override public List<String> batchTranslate(List<String> texts, String src, String tgt) {
                throw new RuntimeException("不应被调用");
            }
        };
        RoadmapComposeService translatingService = new RoadmapComposeService();
        translatingService.setTranslationService(mockTranslator);

        List<DevToArticle> articles = List.of(
                new DevToArticle("Getting Started with eBPF", "https://dev.to/x", "ebpf", 320, "An intro article")
        );
        String reply = translatingService.render(
                translatingService.compose("eBPF", articles, List.of()), List.of(), false);
        assertThat(reply).contains("Getting Started with eBPF");
        assertThat(reply).doesNotContain("【译】");
    }

    @Test
    void fallsBackToOriginalWhenTranslatorReturnsNull() {
        // 翻译服务的 batchTranslate 返回含 null 的列表时，对应位置安全回退为原文
        TranslationService nullTranslator = new TranslationService() {
            @Override public String translate(String text, String src, String tgt) { return null; }
            @Override public List<String> batchTranslate(List<String> texts, String src, String tgt) {
                // 模拟部分翻译失败：偶数位置返回 null
                List<String> results = new java.util.ArrayList<>();
                for (int i = 0; i < texts.size(); i++) {
                    results.add(i % 2 == 0 ? null : "【译】" + texts.get(i));
                }
                return results;
            }
        };
        RoadmapComposeService translatingService = new RoadmapComposeService();
        translatingService.setTranslationService(nullTranslator);

        List<DevToArticle> articles = List.of(
                new DevToArticle("Getting Started with eBPF", "https://dev.to/x", "ebpf", 320, "An intro article")
        );
        String reply = translatingService.render(
                translatingService.compose("eBPF", articles, List.of()), List.of(), true);
        // title 是第0个（偶数）→ null → 回退原文；description 是第1个（奇数）→ 有翻译
        assertThat(reply).contains("Getting Started with eBPF");  // title 回退原文
    }

    @Test
    void composeWithLlmProducesTopicSpecificDescriptions() {
        // 走 LLM 路径时，步骤说明应贴合主题、由 LLM 提供，而非模板的空泛文字
        RoadmapComposeService llmService = new RoadmapComposeService() {
            @Override
            protected RoadmapPlanDto generatePlanDto(String topic, String context) {
                RoadmapPlanDto dto = new RoadmapPlanDto();
                dto.prerequisites = List.of(new RoadmapPlanDto.StepDto("Linux 基础", "熟悉命令行与内核模块加载"));
                dto.coreConcepts = List.of(new RoadmapPlanDto.StepDto("eBPF 程序结构", "理解 kprobe/uprobe 挂载点"));
                dto.projectIdeas = List.of(new RoadmapPlanDto.StepDto("写一个 hello-bpf", "用 bpftool 加载并观察输出"));
                return dto;
            }
        };

        RoadmapPlan plan = llmService.composeWithLlm("eBPF", List.of(), List.of());

        assertThat(plan.coreConcepts()).anyMatch(s -> s.title().contains("eBPF 程序结构"));
        assertThat(plan.coreConcepts()).anyMatch(s -> s.description().contains("kprobe"));
        assertThat(plan.prerequisites()).anyMatch(s -> s.description().contains("内核模块"));
        // 渲染结果不应包含模板的空泛说明
        String reply = llmService.render(plan, List.of());
        assertThat(reply).doesNotContain("掌握变量、循环");
    }

    @Test
    void buildContextIncludesTopicAndMaterials() {
        // 喂给 LLM 的上下文应包含主题与真实资料，便于生成点名资源的说明
        List<DevToArticle> articles = List.of(
                new DevToArticle("Getting Started with eBPF", "https://dev.to/x", "ebpf", 320, "intro")
        );
        List<SearchResult> refs = List.of(
                new SearchResult("eBPF docs", "https://example.com", "docs")
        );

        String ctx = service.buildContext("eBPF", articles, refs);

        assertThat(ctx).contains("eBPF");
        assertThat(ctx).contains("Getting Started with eBPF");
        assertThat(ctx).contains("eBPF docs");
    }

    @Test
    void parsePlanDtoStripsMarkdownFences() {
        // LLM 可能用 ```json 围栏包裹，解析时应兼容
        String fenced = "```json\n{\"prerequisites\":[{\"title\":\"A\",\"description\":\"B\"}]}\n```";
        RoadmapPlanDto dto = service.parsePlanDto(fenced);
        assertThat(dto.prerequisites).hasSize(1);
        assertThat(dto.prerequisites.get(0).title).isEqualTo("A");
        assertThat(dto.prerequisites.get(0).description).isEqualTo("B");
    }

    @Test
    void composeFallsBackToTemplateWhenLlmFails() {
        // plannerClient 非空但 LLM 抛错时，compose 应安全退回模板，不抛异常
        RoadmapComposeService svc = new RoadmapComposeService() {
            @Override
            protected RoadmapPlanDto generatePlanDto(String topic, String context) {
                throw new RuntimeException("LLM down");
            }
        };
        svc.setPlannerClient(mock(ChatClient.class));

        RoadmapPlan plan = svc.compose("eBPF", List.of(), List.of());

        assertThat(plan.prerequisites()).anyMatch(s -> s.description().contains("掌握变量"));
    }
}
