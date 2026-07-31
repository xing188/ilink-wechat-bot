package com.ilink.test.ilinkcheck.application.rag;

import com.ilink.test.ilinkcheck.application.rag.model.RagChunk;
import com.ilink.test.ilinkcheck.application.rag.model.RagContext;
import com.ilink.test.ilinkcheck.application.rag.model.RagQuery;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * RAG 增强组装服务
 * 职责：执行检索后，将分片结果统一格式化，拼装成LLM可直接接收的上下文文本
 * 解耦：把【检索】和【上下文文本格式化】分离
 * RagService：只负责向量检索、路由分发
 * RagAugmentationService：专注检索结果拼接、文本规整，输出完整参考上下文
 */
@Service
public class RagAugmentationService {

    private final RagService ragService;

    public RagAugmentationService(RagService ragService) {
        this.ragService = ragService;
    }

    /**
     * 完整RAG增强入口
     * @param query 检索条件（知识库ID、query文本、topK等）
     * @return 封装查询条件、原始分片、拼装好的上下文文本
     */
    public RagContext augment(RagQuery query) {
        // 调用通用RAG调度器执行检索
        List<RagChunk> chunks = ragService.retrieve(query);
        // 将分片拼接成LLM可读参考文本，封装返回上下文对象
        return new RagContext(query, chunks, buildContextText(chunks));
    }

    /**
     * 将检索分片列表拼接为标准参考资料文本
     * 输出格式适合直接注入SystemPrompt/UserPrompt
     */
    public String buildContextText(List<RagChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        builder.append("检索到的参考资料：").append(System.lineSeparator());
        for (int i = 0; i < chunks.size(); i++) {
            RagChunk chunk = chunks.get(i);
            builder.append(i + 1).append(". ");
            // 拼接分片唯一标识
            if (StringUtils.hasText(chunk.id())) {
                builder.append("[").append(chunk.id()).append("] ");
            }
            // 知识库正文内容
            builder.append(chunk.content());
            // 附带元数据（分类、位置、示例等业务信息）
            if (!chunk.metadata().isEmpty()) {
                builder.append(" | metadata=").append(chunk.metadata());
            }
            builder.append(System.lineSeparator());
        }
        return builder.toString().trim();
    }
}