package com.ilink.test.ilinkcheck.application.rag;

import com.ilink.test.ilinkcheck.application.rag.model.RagChunk;
import com.ilink.test.ilinkcheck.application.rag.model.RagContext;
import com.ilink.test.ilinkcheck.application.rag.model.RagQuery;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * RAG 上下文组装器。
 * 作用是把检索结果整理成可以直接塞给 LLM 的上下文文本，后面接通通用对话链路时就直接用它。
 */
@Service
public class RagAugmentationService {

    private final RagService ragService;

    public RagAugmentationService(RagService ragService) {
        this.ragService = ragService;
    }

    public RagContext augment(RagQuery query) {
        List<RagChunk> chunks = ragService.retrieve(query);
        return new RagContext(query, chunks, buildContextText(chunks));
    }

    public String buildContextText(List<RagChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        builder.append("检索到的参考资料：").append(System.lineSeparator());
        for (int i = 0; i < chunks.size(); i++) {
            RagChunk chunk = chunks.get(i);
            builder.append(i + 1).append(". ");
            if (StringUtils.hasText(chunk.id())) {
                builder.append("[").append(chunk.id()).append("] ");
            }
            builder.append(chunk.content());
            if (!chunk.metadata().isEmpty()) {
                builder.append(" | metadata=").append(chunk.metadata());
            }
            builder.append(System.lineSeparator());
        }
        return builder.toString().trim();
    }
}
