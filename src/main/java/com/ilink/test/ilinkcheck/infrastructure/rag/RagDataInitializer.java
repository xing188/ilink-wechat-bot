package com.ilink.test.ilinkcheck.infrastructure.rag;

import com.ilink.test.ilinkcheck.application.rag.model.RagKnowledgeBase;
import com.ilink.test.ilinkcheck.infrastructure.persistence.review.StyleGuideRepository;
import com.ilink.test.ilinkcheck.infrastructure.persistence.review.StyleGuideRule;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 写作规范知识库初始化器。
 * 规则先落数据库，再统一回灌到向量库，保证应用重启后检索能力仍然可用。
 */
@Component
@Slf4j
public class RagDataInitializer {

    private final StyleGuideRepository repository;
    private final VectorStoreService vectorStoreService;

    public RagDataInitializer(StyleGuideRepository repository, VectorStoreService vectorStoreService) {
        this.repository = repository;
        this.vectorStoreService = vectorStoreService;
    }

    @PostConstruct
    public void init() {
        try {
            List<StyleGuideRule> rules = loadOrSeedRules();
            List<Document> documents = rules.stream()
                    .map(this::toDocument)
                    .toList();
            vectorStoreService.addDocuments(documents);
            log.info("写作规范向量库已就绪，加载规则数={}", rules.size());
        }
        catch (Exception e) {
            log.error("写作规范知识库初始化失败，但不影响应用启动", e);
        }
    }

    private List<StyleGuideRule> loadOrSeedRules() {
        List<StyleGuideRule> existingRules = repository.findAll();
        if (!existingRules.isEmpty()) {
            log.info("检测到数据库已有写作规范规则，数量={}", existingRules.size());
            return existingRules;
        }

        List<StyleGuideRule> defaultRules = List.of(
                new StyleGuideRule("001", "spacing", "中英文",
                        "中文与英文之间需要加空格，例如“Redis 延迟”而不是“Redis延迟”。",
                        "正确：使用 Redis 缓存；错误：使用Redis缓存"),
                new StyleGuideRule("002", "code-block", "代码块",
                        "代码块需要标明语言类型，例如 ```java 而不是 ```。",
                        "正确：```java\\nSystem.out.println()\\n```"),
                new StyleGuideRule("003", "terminology", "大小写",
                        "专业术语需要保持正确大小写，例如 Redis、MySQL、JavaScript。",
                        "正确：Redis；错误：redis"),
                new StyleGuideRule("004", "spacing", "中文数字",
                        "中文与数字之间建议保留空格。",
                        "正确：Java 8；错误：Java8"),
                new StyleGuideRule("005", "punctuation", "标点",
                        "代码块内使用英文标点，正文使用中文标点。",
                        ""),
                new StyleGuideRule("006", "structure", "标题",
                        "文章标题建议使用 ## 或 ### 的层级结构，避免层级过深。",
                        ""),
                new StyleGuideRule("007", "terminology", "专有名词",
                        "首次出现专有名词时建议给出全称，例如 REST（Representational State Transfer）。",
                        "正确：REST（Representational State Transfer）")
        );

        repository.saveAll(defaultRules);
        log.info("数据库中未发现写作规范规则，已写入默认规则数={}", defaultRules.size());
        return defaultRules;
    }

    private Document toDocument(StyleGuideRule rule) {
        return new Document(
                rule.getRule(),
                Map.of(
                        "id", rule.getId(),
                        "knowledgeBase", RagKnowledgeBase.STYLE_GUIDE.id(),
                        "category", rule.getCategory(),
                        "location", rule.getKeyword(),
                        "keyword", rule.getKeyword(),
                        "example", rule.getExample() != null ? rule.getExample() : ""
                )
        );
    }
}
