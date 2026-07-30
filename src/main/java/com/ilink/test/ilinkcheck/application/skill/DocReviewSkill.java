package com.ilink.test.ilinkcheck.application.skill;

import com.ilink.test.ilinkcheck.application.agent.dto.review.ReviewReport;
import com.ilink.test.ilinkcheck.application.agent.feature.review.DraftReviewService;
import org.springframework.stereotype.Component;

/**
 * 文档审稿技能（人5 实现）。
 * <p>
 * 对用户提交的技术文档草稿进行规范化审稿。
 * 底层调用 {@link DraftReviewService}，结合规则引擎与 RAG 向量检索，
 * 输出包含排版建议的结构化审稿报告。
 * </p>
 *
 * <p>由 {@link SkillManager} 自动注册，零硬编码。</p>
 *
 * @see DraftReviewService
 * @see SkillManager
 */
@Component
public class DocReviewSkill implements Skill {

    private final DraftReviewService draftReviewService;

    public DocReviewSkill(DraftReviewService draftReviewService) {
        this.draftReviewService = draftReviewService;
    }

    @Override
    public String id() {
        return "doc-review";
    }

    @Override
    public String name() {
        return "文档审稿";
    }

    @Override
    public String description() {
        return "对技术文档或博客草稿进行规范化审稿，检查中英文空格、代码块标记、术语大小写等排版问题，输出审稿报告与修改建议";
    }

    @Override
    public String execute(String input) {
        if (input == null || input.isBlank()) {
            return "请提供需要审稿的技术文档或博客草稿内容。";
        }

        ReviewReport report = draftReviewService.review(input);
        return draftReviewService.formatReport(report);
    }
}
