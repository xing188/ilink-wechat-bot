package com.ilink.test.ilinkcheck.application.rag.model;

import java.util.List;

/**
 * RAG 增强后的上下文。
 * 后面接到 prompt 时，直接拿这个对象拼 system/user 提示词即可。
 */
public record RagContext(
        RagQuery query,
        List<RagChunk> chunks,
        String contextText
) {

    public RagContext {
        chunks = chunks == null ? List.of() : List.copyOf(chunks);
        contextText = contextText == null ? "" : contextText;
    }
}
