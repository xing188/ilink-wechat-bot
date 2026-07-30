package com.ilink.test.ilinkcheck.application.rag.model;

import java.util.Map;

/**
 * 检索命中的原子片段。
 * 不直接暴露 Spring AI 的 Document，方便后续替换底层向量库实现。
 */
public record RagChunk(
        String id,
        String content,
        Map<String, Object> metadata
) {

    public RagChunk {
        content = content == null ? "" : content.trim();
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
