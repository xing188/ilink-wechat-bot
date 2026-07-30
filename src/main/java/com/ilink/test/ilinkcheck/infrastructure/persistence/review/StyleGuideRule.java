package com.ilink.test.ilinkcheck.infrastructure.persistence.review;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 持久层实体：文档写作规范规则表
 * 对应数据库表：style_guide_rule
 * 用途：存储所有技术文档审稿规范（规则库源头）
 *
 * 分层位置：infrastructure.persistence 持久层JPA实体
 *
 * 数据流关系：
 * DB style_guide_rule表 → StyleGuideRule实体 → 启动加载 → 转为SpringAI Document
 * → 存入向量库 SimpleVectorStore，用于RAG检索
 */
@Entity
@Table(name = "style_guide_rule")
public class StyleGuideRule {

    /** 规则唯一主键 */
    @Id
    @Column(length = 32)
    private String id;

    /**
     * 规范分类，存入Document metadata
     * 例：排版规范、标题规范、命名规范、代码块规范
     */
    @Column(length = 50, nullable = false)
    private String category;

    /**
     * 关键词（预留扩展）
     * 描述这条规则关联的业务关键词，可作为辅助检索
     */
    @Column(length = 100, nullable = false)
    private String keyword;

    /**
     * 规范正文（核心内容）
     * 最终放入Document主体内容content
     */
    @Column(length = 500, nullable = false)
    private String rule;

    /**
     * 正反示例文案
     * 存入Document metadata，后续组装ReviewSuggestion使用
     */
    @Column(length = 500)
    private String example;

    // JPA 必须提供无参构造
    public StyleGuideRule() {}

    public StyleGuideRule(String id, String category, String keyword, String rule, String example) {
        this.id = id;
        this.category = category;
        this.keyword = keyword;
        this.rule = rule;
        this.example = example;
    }

    // getter，当前没有setter，实体只做查询加载，不需要修改
    public String getId() { return id; }
    public String getCategory() { return category; }
    public String getKeyword() { return keyword; }
    public String getRule() { return rule; }
    public String getExample() { return example; }
}