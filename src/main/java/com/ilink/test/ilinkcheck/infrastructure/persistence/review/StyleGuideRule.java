package com.ilink.test.ilinkcheck.infrastructure.persistence.review;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "style_guide_rule")
public class StyleGuideRule {

    @Id
    @Column(length = 32)
    private String id;

    @Column(length = 50, nullable = false)
    private String category;

    @Column(length = 100, nullable = false)
    private String keyword;

    @Column(length = 500, nullable = false)
    private String rule;

    @Column(length = 500)
    private String example;

    public StyleGuideRule() {}

    public StyleGuideRule(String id, String category, String keyword, String rule, String example) {
        this.id = id;
        this.category = category;
        this.keyword = keyword;
        this.rule = rule;
        this.example = example;
    }

    public String getId() { return id; }
    public String getCategory() { return category; }
    public String getKeyword() { return keyword; }
    public String getRule() { return rule; }
    public String getExample() { return example; }
}
