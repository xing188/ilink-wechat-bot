package com.ilink.test.ilinkcheck.application.rag;

import com.ilink.test.ilinkcheck.application.rag.model.RagChunk;
import com.ilink.test.ilinkcheck.application.rag.model.RagQuery;

import java.util.List;

/**
 * 通用 RAG 域检索接口。
 * 一个 retriever 对应一个知识域，例如写作规范、项目文档、学习资料。
 */
public interface RagRetriever {

    String knowledgeBaseId();

    List<RagChunk> retrieve(RagQuery query);
}
