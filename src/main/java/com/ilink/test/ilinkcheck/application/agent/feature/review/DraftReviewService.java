package com.ilink.test.ilinkcheck.application.agent.feature.review;

import com.ilink.test.ilinkcheck.application.agent.dto.review.ReviewReport;
import com.ilink.test.ilinkcheck.application.agent.dto.review.ReviewSuggestion;
import com.ilink.test.ilinkcheck.application.agent.spi.rag.StyleGuideRetriever;
import com.ilink.test.ilinkcheck.infrastructure.document.DocumentExportService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 技术文档审稿核心业务逻辑。
 * <p>
 * 负责对用户提交的技术文档草稿进行规范化审稿，结合规则引擎（中英文空格、代码块标记、
 * 术语大小写等）与 RAG 向量检索（StyleGuideRetriever），输出结构化的审稿报告。
 * </p>
 *
 * <p>边界：审稿逻辑在 feature.review，向量检索在 infrastructure.rag。</p>
 */
@Service
@Slf4j
public class DraftReviewService {

    private final StyleGuideRetriever styleGuideRetriever;
    private final DocumentExportService documentExportService;

    public DraftReviewService(StyleGuideRetriever styleGuideRetriever,
                              DocumentExportService documentExportService) {
        this.styleGuideRetriever = styleGuideRetriever;
        this.documentExportService = documentExportService;
    }

    // ── 规则引擎：内置正则 ──────────────────────────────────────────────

    /** 中英文之间缺少空格 */
    private static final Pattern CN_EN_NO_SPACE = Pattern.compile(
            "[\\u4e00-\\u9fff][A-Za-z]|[A-Za-z][\\u4e00-\\u9fff]");

    /** 中文与数字之间缺少空格 */
    private static final Pattern CN_DIGIT_NO_SPACE = Pattern.compile(
            "[\\u4e00-\\u9fff]\\d|\\d[\\u4e00-\\u9fff]");

    /** 代码块未指定语言：以 ``` 开头后紧跟换行（即没有语言标识） */
    private static final Pattern CODE_BLOCK_NO_LANG = Pattern.compile(
            "```\\s*\\n");

    /** 常见专业术语大小写检查 */
    private static final Pattern[] TERM_PATTERNS = {
            Pattern.compile("\\bredis\\b"),
            Pattern.compile("\\bjavascript\\b"),
            Pattern.compile("\\btypescript\\b"),
            Pattern.compile("\\bgithub\\b"),
            Pattern.compile("\\bgitlab\\b"),
            Pattern.compile("\\bmacos\\b"),
            Pattern.compile("\\bios\\b"),
            Pattern.compile("\\bapi\\b(?!\\s*(gateway|endpoint|call|route))"),
    };

    private static final String[] TERM_CORRECTIONS = {
            "Redis", "JavaScript", "TypeScript", "GitHub", "GitLab", "macOS", "iOS", "API"
    };

    /**
     * 对草稿执行完整审稿流程。
     *
     * @param draftText 用户提交的技术文档草稿
     * @return 审稿报告，包含所有修改建议
     */
    public ReviewReport review(String draftText) {
        if (draftText == null || draftText.isBlank()) {
            return new ReviewReport(true, List.of(), "草稿为空，无需审稿。");
        }

        List<ReviewSuggestion> suggestions = new ArrayList<>();

        // 1. 内置规则引擎检查
        suggestions.addAll(checkSpacing(draftText));
        suggestions.addAll(checkCodeBlocks(draftText));
        suggestions.addAll(checkTerminology(draftText));

        // 2. RAG 向量检索检查（基于知识库规则）
        try {
            List<ReviewSuggestion> ragSuggestions = styleGuideRetriever.check(draftText);
            if (ragSuggestions != null && !ragSuggestions.isEmpty()) {
                suggestions.addAll(ragSuggestions);
            }
        } catch (Exception e) {
            log.warn("RAG 检索暂时不可用，仅使用规则引擎结果。原因：{}", e.getMessage());
        }

        // 3. 生成报告
        boolean passed = suggestions.isEmpty();
        String summary = buildSummary(suggestions);

        return new ReviewReport(passed, suggestions, summary);
    }

    /**
     * 将审稿报告格式化为用户可读文本。
     */
    public String formatReport(ReviewReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("📝 技术文档审稿报告\n\n");
        sb.append("📊 结论：\n");
        sb.append(report.summary()).append("\n");

        if (!report.suggestions().isEmpty()) {
            sb.append("\n📋 具体建议：\n");
            int index = 1;
            for (ReviewSuggestion s : report.suggestions()) {
                sb.append(index++).append(". [").append(s.type()).append("] ");
                if (s.location() != null && !s.location().isBlank()) {
                    sb.append(s.location()).append("：");
                }
                sb.append(s.message());
                if (s.suggestion() != null && !s.suggestion().isBlank()) {
                    sb.append("\n   💡 建议：").append(s.suggestion());
                }
                sb.append("\n");
            }
        } else {
            sb.append("\n✅ 本轮没有发现明显问题，建议人工再看一遍标题层级和段落节奏。\n");
        }

        sb.append("\n👉 回复「一键优化」可为您直接替换为排版完全合规的 Markdown 文本！");
        return sb.toString();
    }

    /**
     * 导出审稿报告为 .docx 字节数组，复用 {@link DocumentExportService}。
     */
    public byte[] exportReport(String title, String reportText) {
        return documentExportService.exportToDocx(title, reportText);
    }

    // ── 私有规则检查方法 ──────────────────────────────────────────────

    private List<ReviewSuggestion> checkSpacing(String text) {
        List<ReviewSuggestion> result = new ArrayList<>();

        if (CN_EN_NO_SPACE.matcher(text).find()) {
            result.add(new ReviewSuggestion(
                    "spacing",
                    "全文",
                    "中文与英文之间需加空格（盘古之白）",
                    "例如「Redis 延迟」而非「Redis延迟」，「使用 Java」而非「使用Java」"
            ));
        }
        if (CN_DIGIT_NO_SPACE.matcher(text).find()) {
            result.add(new ReviewSuggestion(
                    "spacing",
                    "全文",
                    "中文与数字之间需加空格",
                    "例如「Java 8」而非「Java8」，「第 1 步」而非「第1步」"
            ));
        }
        return result;
    }

    private List<ReviewSuggestion> checkCodeBlocks(String text) {
        List<ReviewSuggestion> result = new ArrayList<>();

        if (CODE_BLOCK_NO_LANG.matcher(text).find()) {
            result.add(new ReviewSuggestion(
                    "code-block",
                    "代码块",
                    "代码块需指定语言类型",
                    "建议写为 ```java、```bash、```python 等，而非裸 ```"
            ));
        }
        return result;
    }

    private List<ReviewSuggestion> checkTerminology(String text) {
        List<ReviewSuggestion> result = new ArrayList<>();
        String lower = text.toLowerCase();

        for (int i = 0; i < TERM_PATTERNS.length; i++) {
            if (TERM_PATTERNS[i].matcher(lower).find()) {
                // 检查原文中是否已经是正确拼写
                if (!text.contains(TERM_CORRECTIONS[i])) {
                    result.add(new ReviewSuggestion(
                            "terminology",
                            "全文",
                            "专业术语需保持正确大小写",
                            "建议将相关术语统一规范为 " + TERM_CORRECTIONS[i]
                    ));
                }
            }
        }
        return result;
    }

    private String buildSummary(List<ReviewSuggestion> suggestions) {
        if (suggestions.isEmpty()) {
            return "本轮没有发现明显问题，建议人工再看一遍标题层级和段落节奏。";
        }

        long spacingCount = suggestions.stream().filter(s -> "spacing".equals(s.type())).count();
        long codeBlockCount = suggestions.stream().filter(s -> "code-block".equals(s.type())).count();
        long terminologyCount = suggestions.stream().filter(s -> "terminology".equals(s.type())).count();
        long otherCount = suggestions.size() - spacingCount - codeBlockCount - terminologyCount;

        StringBuilder sb = new StringBuilder();
        sb.append("本轮共发现 ").append(suggestions.size()).append(" 处建议修改项");
        if (spacingCount > 0) sb.append("（排版间距 ").append(spacingCount).append(" 处");
        if (codeBlockCount > 0) {
            sb.append(spacingCount > 0 ? "，" : "（").append("代码块 ").append(codeBlockCount).append(" 处");
        }
        if (terminologyCount > 0) {
            sb.append(spacingCount > 0 || codeBlockCount > 0 ? "，" : "（").append("术语大小写 ").append(terminologyCount).append(" 处");
        }
        if (otherCount > 0) {
            sb.append(spacingCount > 0 || codeBlockCount > 0 || terminologyCount > 0 ? "，" : "（").append("其他 ").append(otherCount).append(" 处");
        }
        sb.append("）。");
        return sb.toString();
    }
}
