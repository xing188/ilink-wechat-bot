package com.ilink.test.ilinkcheck.infrastructure.rag;

import com.ilink.test.ilinkcheck.infrastructure.persistence.review.StyleGuideRepository;
import com.ilink.test.ilinkcheck.infrastructure.persistence.review.StyleGuideRule;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

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
            if (repository.count() > 0) {
                log.info("规范规则库已存在，跳过初始化");
                return;
            }

            List<StyleGuideRule> rules = List.of(
                    new StyleGuideRule("001", "spacing", "中英文",
                            "中文与英文之间需加空格，例如“Redis 延迟”而非“Redis延迟”。",
                            "正确：使用 Redis 缓存  错误：使用Redis缓存"),
                    new StyleGuideRule("002", "code-block", "代码块",
                            "代码块需指定语言类型，例如 ```java 而非 ```。",
                            "正确：```java\nSystem.out.println()\n```"),
                    new StyleGuideRule("003", "terminology", "大小写",
                            "专业术语需保持正确大小写，例如 Redis、MySQL、JavaScript。",
                            "正确：Redis 错误：redis"),
                    new StyleGuideRule("004", "spacing", "中文数字",
                            "中文与数字之间需加空格。",
                            "正确：Java 8 错误：Java8"),
                    new StyleGuideRule("005", "punctuation", "标点",
                            "代码块内使用英文标点，正文使用中文标点。", ""),
                    new StyleGuideRule("006", "structure", "标题",
                            "文章标题需使用 ## 或 ### 层级结构，最多不超过四级标题。", ""),
                    new StyleGuideRule("007", "terminology", "专有名词",
                            "首次出现专有名词时应给出全称，例如“REST（Representational State Transfer）”。",
                            "正确：REST（Representational State Transfer）")
            );

            repository.saveAll(rules);
            List<Document> docs = rules.stream()
                    .map(rule -> new Document(
                            rule.getRule(),
                            Map.of("id", rule.getId(), "category", rule.getCategory(),
                                    "keyword", rule.getKeyword(),
                                    "example", rule.getExample() != null ? rule.getExample() : "")))
                    .toList();
            vectorStoreService.addDocuments(docs);
            log.info("规范规则库已初始化，共 {} 条规则", rules.size());
        }
        catch (Exception e) {
            log.error("规范规则库初始化失败，不影响启动", e);
        }
    }
}
