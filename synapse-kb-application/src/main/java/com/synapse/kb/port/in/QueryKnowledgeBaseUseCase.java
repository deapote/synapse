package com.synapse.kb.port.in;

import com.synapse.kb.model.Query;
import com.synapse.kb.model.RagContext;

/**
 * 知识库问答用例。
 *
 * <p>入站端口（Driving Port），负责检索相关文档片段并组装 prompt。
 * 返回的 {@link RagContext} 包含组装好的 prompt 和引用来源，
 * 由适配器层决定是同步生成 {@link com.synapse.kb.model.Answer} 还是流式推送。
 */
public interface QueryKnowledgeBaseUseCase {

    /**
     * 准备 RAG 检索上下文。
     *
     * @param query 用户查询
     * @return 包含组装后 prompt 和引用来源的检索上下文
     */
    RagContext prepare(Query query);
}
