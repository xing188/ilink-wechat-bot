package com.ilink.test.ilinkcheck.adapter.inbound.web;

import com.ilink.test.ilinkcheck.application.agent.dto.review.ReviewReport;
import com.ilink.test.ilinkcheck.application.agent.feature.review.DraftReviewService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 审稿工作台 Web 接口。
 * 复用 {@link DraftReviewService} 的规则引擎 + RAG 审稿、自动修复与 docx 导出能力。
 */
@RestController
@RequestMapping("/api/review")
public class ReviewController {

    private final DraftReviewService draftReviewService;

    public ReviewController(DraftReviewService draftReviewService) {
        this.draftReviewService = draftReviewService;
    }

    public record ReviewRequest(String draftText) {
    }

    public record ExportRequest(String title, String reportText) {
    }

    @PostMapping
    public Map<String, Object> review(@RequestBody ReviewRequest request) {
        String draft = request == null || request.draftText() == null ? "" : request.draftText();
        ReviewReport report = draftReviewService.review(draft);
        return Map.of(
                "passed", report.passed(),
                "summary", report.summary(),
                "suggestions", report.suggestions(),
                "formattedReport", draftReviewService.formatReport(report)
        );
    }

    @PostMapping("/fix")
    public Map<String, Object> fix(@RequestBody ReviewRequest request) {
        String draft = request == null || request.draftText() == null ? "" : request.draftText();
        return Map.of("fixedText", draftReviewService.applyFixes(draft));
    }

    @PostMapping("/export")
    public ResponseEntity<byte[]> export(@RequestBody ExportRequest request) {
        String title = request == null || !StringUtils.hasText(request.title()) ? "技术文档审稿报告" : request.title().trim();
        String reportText = request == null || request.reportText() == null ? "" : request.reportText();
        byte[] docx = draftReviewService.exportReport(title, reportText);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"review-report.docx\"")
                .body(docx);
    }
}
