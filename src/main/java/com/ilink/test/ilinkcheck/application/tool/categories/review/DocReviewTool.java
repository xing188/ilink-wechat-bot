package com.ilink.test.ilinkcheck.application.tool.categories.review;

import com.ilink.test.ilinkcheck.application.agent.dto.review.ReviewReport;
import com.ilink.test.ilinkcheck.application.agent.feature.review.DraftReviewService;
import com.ilink.test.ilinkcheck.application.tool.ChatFunctionTool;
import com.ilink.test.ilinkcheck.application.tool.ToolMetadata;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 文档审稿工具（人5 实现）。
 * <p>
 * 暴露给 AI 模型调用，对用户提交的技术文档草稿执行规范化审稿，
 * 检查中英文空格、代码块标记、术语大小写等规范问题。
 * </p>
 *
 * <p>边界：Tool 只做薄层转发，审稿逻辑在 {@link DraftReviewService}。</p>
 */
@Component
public class DocReviewTool implements ChatFunctionTool {

    private final DraftReviewService draftReviewService;

    @Override
    public ToolMetadata metadata() {
        return new ToolMetadata(
                "docReview",
                "审阅技术文档或博客草稿的排版规范，检查中英文空格、代码块标记、术语大小写等问题并输出审稿报告。",
                List.of("审稿", "审一下", "博客草稿", "文档规范", "排版", "review", "检查格式", "文档审稿"),
                List.of("帮我审一下这篇博客", "检查这篇文章的格式问题", "帮我审稿"),
                "Use this tool when the user asks to review, proofread, or check the formatting of a technical document or blog draft."
        );
    }

    public DocReviewTool(DraftReviewService draftReviewService) {
        this.draftReviewService = draftReviewService;
    }

    @Tool(name = "reviewDocument",
            description = "对技术文档或博客草稿进行规范化审稿，检查中英文空格、代码块标记、术语大小写等排版规范问题")
    public String reviewDocument(
            @ToolParam(required = true, description = "待审稿的技术文档或博客草稿全文")
            String draftText) {

        if (draftText == null || draftText.isBlank()) {
            return "请提供需要审稿的文档内容。";
        }

        ReviewReport report = draftReviewService.review(draftText);
        return draftReviewService.formatReport(report);
    }

    @Tool(name = "exportReviewReport",
            description = "将审稿报告导出为 .docx 文件供下载")
    public String exportReviewReport(
            @ToolParam(required = true, description = "审稿报告标题")
            String title,
            @ToolParam(required = true, description = "已生成的审稿报告内容")
            String reportText) {

        if (title == null || title.isBlank()) {
            title = "技术文档审稿报告";
        }
        if (reportText == null || reportText.isBlank()) {
            return "请先执行审稿，再导出报告。";
        }

        draftReviewService.exportReport(title, reportText);
        return "审稿报告已导出为 " + title + ".docx，文件正在发送中。";
    }
}
