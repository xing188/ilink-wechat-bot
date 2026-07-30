package com.ilink.test.ilinkcheck.application.agent.feature.roadmap;

import com.ilink.test.ilinkcheck.application.agent.dto.roadmap.DevToArticle;
import com.ilink.test.ilinkcheck.application.agent.dto.roadmap.RoadmapPlan;
import com.ilink.test.ilinkcheck.application.agent.dto.roadmap.RoadmapStep;
import com.ilink.test.ilinkcheck.application.agent.dto.roadmap.SearchResult;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 技术路线业务逻辑层（人4 核心交付之一）。
 *
 * <p>职责：把"主题 + Dev.to 文章 + WebSearch 补充参考"整理成结构化的学习路线。
 * 当前是模板生成，后续可接入 LLM 做 CoT 编排（把 articles / references 当上下文），
 * 调用方（PhaseExecutor 与 Skill）无需改动。</p>
 *
 * <p>设计要点：
 * <ul>
 *   <li>{@link #compose} 只负责产出结构化 {@link RoadmapPlan}；</li>
 *   <li>{@link #render} 只负责把 plan + references 渲染成 Markdown；</li>
 *   <li>每条资料的来源（如 GitHub / Dev.to）由链接域名在 {@link #deriveSource} 中自动推断，
 *       并以"（来源：X）"形式标注在标题旁；</li>
 *   <li>{@link #buildReply} 是给 Skill / Tool 用的"一站式"便捷方法。</li>
 * </ul>
 * </p>
 */
@Service
public class RoadmapComposeService {

    /**
     * 已知站点域名 → 友好来源名。新增站点只需在此补充，无需改动渲染逻辑。
     */
    private static final Map<String, String> KNOWN_SOURCES = Map.ofEntries(
            Map.entry("github.com", "GitHub"),
            Map.entry("gist.github.com", "GitHub Gist"),
            Map.entry("gitlab.com", "GitLab"),
            Map.entry("gitee.com", "Gitee"),
            Map.entry("dev.to", "Dev.to"),
            Map.entry("stackoverflow.com", "Stack Overflow"),
            Map.entry("medium.com", "Medium"),
            Map.entry("youtube.com", "YouTube"),
            Map.entry("www.youtube.com", "YouTube"),
            Map.entry("bilibili.com", "哔哩哔哩"),
            Map.entry("zhihu.com", "知乎"),
            Map.entry("juejin.cn", "掘金"),
            Map.entry("csdn.net", "CSDN"),
            Map.entry("spring.io", "Spring 官方文档"),
            Map.entry("cloud.spring.io", "Spring 官方文档"),
            Map.entry("docs.spring.io", "Spring 官方文档"),
            Map.entry("docs.oracle.com", "Oracle 官方文档"),
            Map.entry("kubernetes.io", "Kubernetes 官方文档"),
            Map.entry("docs.python.org", "Python 官方文档"),
            Map.entry("python.org", "Python 官方文档"),
            Map.entry("react.dev", "React 官方文档"),
            Map.entry("developer.mozilla.org", "MDN Web Docs")
    );

    /**
     * 常见前缀子域，推断来源名时忽略（如 docs.spring.io → Spring）。
     */
    private static final Set<String> COMMON_PREFIX_SUBDOMAINS = Set.of(
            "www", "docs", "doc", "blog", "api", "guide", "learn", "developer", "cn", "en"
    );

    public RoadmapPlan compose(String topic, List<DevToArticle> articles, List<SearchResult> references) {
        List<RoadmapStep> prerequisites = List.of(
                RoadmapStep.of("基础编程语法", "掌握变量、循环、条件判断、函数定义等核心语法"),
                RoadmapStep.of("命令行与调试能力", "熟悉常用 shell 命令与 IDE 断点调试技巧"),
                RoadmapStep.of("阅读英文文档的基本能力", "能独立阅读官方文档与技术社区讨论")
        );
        List<RoadmapStep> coreConcepts = List.of(
                RoadmapStep.of(topic + " 的核心概念", topic + " 的核心原理与设计思想"),
                RoadmapStep.of(topic + " 的运行机制", topic + " 在实际环境中的执行流程"),
                RoadmapStep.of(topic + " 的常见约束", topic + " 使用时的限制与注意事项")
        );
        List<RoadmapStep> projectIdeas = List.of(
                RoadmapStep.of("做一个最小 demo", "跑通官方 Hello World 或最小可用示例"),
                RoadmapStep.of("完成一个可复现实践", "基于真实场景完成一个小功能并记录步骤"),
                RoadmapStep.of("整理一篇学习笔记", "输出学习总结，可分享给团队或社区")
        );
        return new RoadmapPlan(
                topic,
                prerequisites,
                coreConcepts,
                projectIdeas,
                articles == null ? List.of() : articles
        );
    }

    /**
     * 将 {@link RoadmapPlan} 渲染为 Markdown 格式的学习路线回复。
     *
     * <p>每个阶段下的步骤会渲染为"标题：说明"格式。如果某步骤的 description 为空，
     * 则只显示标题（向后兼容旧数据）。</p>
     */
    public String render(RoadmapPlan plan, List<SearchResult> references) {
        List<DevToArticle> articles = plan.recommendedArticles();

        String articleText = (articles == null || articles.isEmpty())
                ? "- 暂无 Dev.to 推荐文章"
                : articles.stream()
                .map(this::formatArticleLine)
                .reduce((a, b) -> a + System.lineSeparator() + b)
                .orElse("- 暂无 Dev.to 推荐文章");

        String referenceText = (references == null || references.isEmpty())
                ? "- 暂无补充检索结果"
                : references.stream()
                .map(this::formatReferenceLine)
                .reduce((a, b) -> a + System.lineSeparator() + b)
                .orElse("- 暂无补充检索结果");

        return """
                %s 学习路线

                第一阶段：预备知识
                %s

                第二阶段：核心概念
                %s

                第三阶段：实战建议
                %s

                推荐资料
                %s

                补充参考
                %s
                """.formatted(
                plan.topic(),
                renderSteps(plan.prerequisites()),
                renderSteps(plan.coreConcepts()),
                renderSteps(plan.projectIdeas()),
                articleText,
                referenceText
        ).trim();
    }

    /**
     * 一站式便捷方法：先 compose 再 render，给 Skill / Tool 这类"自包含"调用方使用。
     */
    public String buildReply(String topic, List<DevToArticle> articles, List<SearchResult> references) {
        return render(compose(topic, articles, references), references);
    }

    // ── 内部方法 ──

    /**
     * 将单篇推荐文章渲染为带可点击链接与来源标注的 Markdown 行。
     * 链接格式：[标题](url)。来源（如 GitHub / Dev.to）由链接域名自动推断，
     * 显示在标题旁，格式为"（来源：X）"。若文章 url 为空则退化为纯标题。
     */
    private String formatArticleLine(DevToArticle article) {
        String titlePart = isLinkable(article.url())
                ? "[" + article.title() + "](" + article.url() + ")"
                : article.title();
        return "- " + titlePart
                + "（来源：" + deriveSource(article.url()) + "）"
                + "：" + defaultText(article.description(), "暂无摘要")
                + "（👍 " + article.positiveReactions() + "）";
    }

    /**
     * 将单条补充参考渲染为带可点击链接与来源标注的 Markdown 行。
     * 链接格式：[标题](url)。来源由链接域名自动推断，显示在标题旁。
     */
    private String formatReferenceLine(SearchResult result) {
        String titlePart = isLinkable(result.url())
                ? "[" + result.title() + "](" + result.url() + ")"
                : result.title();
        return "- " + titlePart
                + "（来源：" + deriveSource(result.url()) + "）"
                + "：" + defaultText(result.snippet(), "暂无摘要");
    }

    /**
     * 根据链接域名推断资料的来源说明。
     *
     * <p>优先匹配已知站点（GitHub / Dev.to / Stack Overflow 等）给出友好的中文/品牌名；
     * 未知域名则回退为"去掉常见前缀子域 + 首字母大写"的可读名称（如 example.com → Example）。
     * 链接为空或无法解析时返回"未知来源"。</p>
     */
    String deriveSource(String url) {
        if (url == null || url.isBlank()) {
            return "未知来源";
        }
        try {
            String host = URI.create(url).getHost();
            if (host == null || host.isBlank()) {
                return "未知来源";
            }
            host = host.toLowerCase();
            if (host.startsWith("www.")) {
                host = host.substring(4);
            }
            if (KNOWN_SOURCES.containsKey(host)) {
                return KNOWN_SOURCES.get(host);
            }
            return capitalize(deriveCoreLabel(host));
        } catch (Exception ignored) {
            return "未知来源";
        }
    }

    /**
     * 从域名中提取可读的核心名称：去掉 docs / blog / api 等常见前缀子域，取有意义的主体段。
     */
    private String deriveCoreLabel(String host) {
        String[] parts = host.split("\\.");
        int start = 0;
        if (parts.length > 2 && COMMON_PREFIX_SUBDOMAINS.contains(parts[0])) {
            start = 1;
        }
        return parts[start];
    }

    private String capitalize(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    /**
     * 判断 url 是否可渲染为链接（非空且符合 http/https 协议）。
     */
    private boolean isLinkable(String url) {
        return url != null && !url.isBlank()
                && (url.startsWith("http://") || url.startsWith("https://"));
    }

    /**
     * 将步骤列表渲染为 Markdown bullet 列表。
     * 有 description 时输出 "- 标题：说明"，无 description 时只输出 "- 标题"。
     */
    private String renderSteps(List<RoadmapStep> steps) {
        if (steps == null || steps.isEmpty()) {
            return "- （暂无）";
        }
        return steps.stream()
                .map(step -> {
                    if (step.description() == null || step.description().isBlank()) {
                        return "- " + step.title();
                    }
                    return "- " + step.title() + "：" + step.description();
                })
                .reduce((a, b) -> a + System.lineSeparator() + b)
                .orElse("- （暂无）");
    }

    private String defaultText(String text, String fallback) {
        return text == null || text.isBlank() ? fallback : text.trim();
    }
}
