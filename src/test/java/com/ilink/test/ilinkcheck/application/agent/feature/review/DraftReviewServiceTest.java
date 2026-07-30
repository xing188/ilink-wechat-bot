package com.ilink.test.ilinkcheck.application.agent.feature.review;

import com.ilink.test.ilinkcheck.application.agent.dto.review.ReviewReport;
import com.ilink.test.ilinkcheck.application.agent.dto.review.ReviewSuggestion;
import com.ilink.test.ilinkcheck.application.agent.spi.rag.StyleGuideRetriever;
import com.ilink.test.ilinkcheck.infrastructure.document.DocumentExportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * DraftReviewService 单元测试。
 * 验证规则引擎 + RAG 协作、边界处理与格式化输出。
 */
class DraftReviewServiceTest {

    private DraftReviewService service;
    private StyleGuideRetriever styleGuideRetriever;
    private DocumentExportService documentExportService;

    @BeforeEach
    void setUp() {
        styleGuideRetriever = mock(StyleGuideRetriever.class);
        documentExportService = mock(DocumentExportService.class);
        service = new DraftReviewService(styleGuideRetriever, documentExportService);
    }

    // ── 边界与空值 ────────────────────────────────────────────────

    @Test
    @DisplayName("空草稿应返回通过且无建议")
    void shouldReturnPassedForEmptyDraft() {
        ReviewReport report = service.review("");
        assertThat(report.passed()).isTrue();
        assertThat(report.suggestions()).isEmpty();
        assertThat(report.summary()).contains("草稿为空");
    }

    @Test
    @DisplayName("null 草稿应返回通过且无建议")
    void shouldReturnPassedForNullDraft() {
        ReviewReport report = service.review(null);
        assertThat(report.passed()).isTrue();
        assertThat(report.suggestions()).isEmpty();
    }

    @Test
    @DisplayName("纯英文草稿应跳过中文规则检查")
    void shouldSkipCnRulesForEnglishOnlyDraft() {
        String draft = "This is a simple Redis usage guide. Use the API to connect.";
        when(styleGuideRetriever.check(draft)).thenReturn(List.of());

        ReviewReport report = service.review(draft);

        // 纯英文没有中英文空格问题
        long spacingCount = report.suggestions().stream()
                .filter(s -> "spacing".equals(s.type())).count();
        assertThat(spacingCount).isZero();
    }

    // ── 规则引擎：中英文空格 ──────────────────────────────────────

    @Test
    @DisplayName("检测中英文直接贴合（中英文空格缺失）")
    void shouldDetectCnEnSpacingIssue() {
        String draft = "使用Redis缓存可以提升性能。";
        when(styleGuideRetriever.check(draft)).thenReturn(List.of());

        ReviewReport report = service.review(draft);

        assertThat(report.suggestions()).anyMatch(s ->
                "spacing".equals(s.type()) && s.message().contains("盘古之白"));
    }

    @Test
    @DisplayName("检测中文与数字贴合")
    void shouldDetectCnDigitSpacingIssue() {
        String draft = "Java8是里程碑版本。";
        when(styleGuideRetriever.check(draft)).thenReturn(List.of());

        ReviewReport report = service.review(draft);

        assertThat(report.suggestions()).anyMatch(s ->
                "spacing".equals(s.type()) && s.message().contains("数字"));
    }

    @Test
    @DisplayName("规范排版不触发空格警告")
    void shouldNotTriggerSpacingForWellFormattedText() {
        String draft = "使用 Redis 缓存可以提升性能。Java 8 是里程碑版本。";
        when(styleGuideRetriever.check(draft)).thenReturn(List.of());

        ReviewReport report = service.review(draft);

        long spacingCount = report.suggestions().stream()
                .filter(s -> "spacing".equals(s.type())).count();
        assertThat(spacingCount).isZero();
    }

    // ── 规则引擎：代码块 ─────────────────────────────────────────

    @Test
    @DisplayName("检测未指定语言的代码块")
    void shouldDetectCodeBlockWithoutLanguage() {
        String draft = "运行以下命令：\n```\nINFO commandstats\n```";
        when(styleGuideRetriever.check(draft)).thenReturn(List.of());

        ReviewReport report = service.review(draft);

        assertThat(report.suggestions()).anyMatch(s ->
                "code-block".equals(s.type()));
    }

    @Test
    @DisplayName("已指定语言的代码块不触发警告")
    void shouldNotTriggerForLabeledCodeBlock() {
        String draft = "如下所示：\n```bash\nINFO commandstats\n```";
        when(styleGuideRetriever.check(draft)).thenReturn(List.of());

        ReviewReport report = service.review(draft);

        long codeBlockCount = report.suggestions().stream()
                .filter(s -> "code-block".equals(s.type())).count();
        assertThat(codeBlockCount).isZero();
    }

    // ── 规则引擎：术语大小写 ────────────────────────────────────

    @Test
    @DisplayName("检测小写 redis 术语")
    void shouldDetectLowercaseRedis() {
        String draft = "使用redis作为缓存中间件。";
        when(styleGuideRetriever.check(draft)).thenReturn(List.of());

        ReviewReport report = service.review(draft);

        assertThat(report.suggestions()).anyMatch(s ->
                "terminology".equals(s.type()) && s.suggestion().contains("Redis"));
    }

    @Test
    @DisplayName("正确大小写的术语不触发警告")
    void shouldNotTriggerForCorrectTerminology() {
        String draft = "使用 Redis 作为缓存中间件，基于 JavaScript 和 TypeScript 开发。";
        when(styleGuideRetriever.check(draft)).thenReturn(List.of());

        ReviewReport report = service.review(draft);

        long terminologyCount = report.suggestions().stream()
                .filter(s -> "terminology".equals(s.type())).count();
        assertThat(terminologyCount).isZero();
    }

    // ── RAG 集成 ─────────────────────────────────────────────────

    @Test
    @DisplayName("RAG 检索结果与规则引擎结果合并")
    void shouldMergeRagAndRuleSuggestions() {
        String draft = "使用redis的API调用。";  // 触发 spacing + terminology
        ReviewSuggestion ragSuggestion = new ReviewSuggestion(
                "structure", "正文", "文章结构建议增加小标题", "建议使用 ## 分级");
        when(styleGuideRetriever.check(draft)).thenReturn(List.of(ragSuggestion));

        ReviewReport report = service.review(draft);

        // 应包含规则引擎发现 + RAG 发现
        assertThat(report.suggestions().size()).isGreaterThanOrEqualTo(2);
        assertThat(report.suggestions()).anyMatch(s -> "structure".equals(s.type()));
        assertThat(report.suggestions()).anyMatch(s -> "spacing".equals(s.type()));
    }

    @Test
    @DisplayName("RAG 异常时自动降级为仅规则引擎")
    void shouldFallbackToRuleOnlyWhenRagFails() {
        String draft = "使用redis的API调用。";
        when(styleGuideRetriever.check(draft)).thenThrow(new RuntimeException("Vector store unavailable"));

        ReviewReport report = service.review(draft);

        // 规则引擎结果应正常返回
        assertThat(report.suggestions()).isNotEmpty();
        long spacingCount = report.suggestions().stream()
                .filter(s -> "spacing".equals(s.type())).count();
        assertThat(spacingCount).isGreaterThan(0);
    }

    @Test
    @DisplayName("RAG 返回空列表不应影响规则引擎结果")
    void shouldReturnRuleResultsWhenRagReturnsEmpty() {
        String draft = "使用redis缓存。";
        when(styleGuideRetriever.check(draft)).thenReturn(List.of());

        ReviewReport report = service.review(draft);

        assertThat(report.suggestions()).isNotEmpty();  // 规则引擎应发现 spacing 问题
    }

    @Test
    @DisplayName("RAG 返回 null 应被安全处理")
    void shouldHandleNullRagResult() {
        String draft = "使用redis缓存。";
        when(styleGuideRetriever.check(draft)).thenReturn(null);

        ReviewReport report = service.review(draft);

        // 不应崩溃，规则引擎结果应正常
        assertThat(report.suggestions()).isNotEmpty();
    }

    // ── 报告格式化 ──────────────────────────────────────────────

    @Test
    @DisplayName("formatReport 输出包含必要结构")
    void shouldFormatReportWithProperStructure() {
        ReviewReport report = new ReviewReport(false, List.of(
                new ReviewSuggestion("spacing", "正文", "中英文空格缺失", "Redis 延迟"),
                new ReviewSuggestion("terminology", "全文", "术语需大写", "Redis")
        ), "共发现 2 处问题。");

        String formatted = service.formatReport(report);

        assertThat(formatted).contains("技术文档审稿报告");
        assertThat(formatted).contains("结论");
        assertThat(formatted).contains("具体建议");
        assertThat(formatted).contains("[spacing]");
        assertThat(formatted).contains("[terminology]");
        assertThat(formatted).contains("Redis 延迟");
        assertThat(formatted).contains("一键优化");
    }

    @Test
    @DisplayName("formatReport 空报告输出通过信息")
    void shouldFormatEmptyReportAsPassed() {
        ReviewReport report = new ReviewReport(true, List.of(), "本轮没有发现明显问题。");

        String formatted = service.formatReport(report);

        assertThat(formatted).contains("没有发现明显问题");
        assertThat(formatted).doesNotContain("[spacing]");
    }

    // ── 综合场景 ────────────────────────────────────────────────

    @Test
    @DisplayName("典型技术博客草稿应发现多种问题")
    void shouldFindMultipleIssuesInTypicalDraft() {
        String draft = """
                Redis延迟排查指南

                这篇文章主要讨论 redis延迟 的排查路径。

                ```
                INFO commandstats
                ```

                使用redis-cli可以连接服务器，检查macos系统上的redis配置。
                """;
        when(styleGuideRetriever.check(draft)).thenReturn(List.of(
                new ReviewSuggestion("structure", "标题", "建议使用 ## 二级标题", "")
        ));

        ReviewReport report = service.review(draft);

        assertThat(report.passed()).isFalse();
        // 应发现：中英文空格(Redis延迟) + 术语(redis) + 代码块(```无语言) +
        //          spacing(redis-cli) + 术语(macos) + RAG建议(structure)
        assertThat(report.suggestions().size()).isGreaterThanOrEqualTo(3);
    }

    // ── 导出功能 ─────────────────────────────────────────────────

    @Test
    @DisplayName("exportReport 委托给 DocumentExportService")
    void shouldDelegateExportToDocumentExportService() {
        byte[] expected = new byte[]{1, 2, 3};
        when(documentExportService.exportToDocx("审稿报告", "内容")).thenReturn(expected);

        byte[] result = service.exportReport("审稿报告", "内容");

        assertThat(result).isSameAs(expected);
        verify(documentExportService).exportToDocx("审稿报告", "内容");
    }
}
