package com.ilink.test.ilinkcheck.application.rag.model;

/**
 * 通用 RAG 知识域枚举。
 * 先把“知识从哪里来”这件事抽象出来，后面新增域时只需要加一个枚举值和一个 Retriever。
 */
public enum RagKnowledgeBase {

    STYLE_GUIDE("style-guide", "写作规范"),
    CODE_EXPLANATION("code-explanation", "代码解释"),
    PROJECT_DOC("project-doc", "项目文档"),
    LEARNING_MATERIAL("learning-material", "学习资料");

    private final String id;
    private final String description;

    RagKnowledgeBase(String id, String description) {
        this.id = id;
        this.description = description;
    }

    public String id() {
        return id;
    }

    public String description() {
        return description;
    }

    public static RagKnowledgeBase fromId(String id) {
        for (RagKnowledgeBase knowledgeBase : values()) {
            if (knowledgeBase.id.equals(id)) {
                return knowledgeBase;
            }
        }
        throw new IllegalArgumentException("Unknown rag knowledge base: " + id);
    }
}
