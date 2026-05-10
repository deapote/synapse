package com.synapse.kb.port.in;

import com.synapse.kb.model.KnowledgeBaseId;

/**
 * 删除知识库用例。
 *
 * <p>入站端口（Driving Port），删除知识库元数据并级联清理其下所有文档及向量数据。
 */
public interface DeleteKnowledgeBaseUseCase {

    /**
     * 删除指定知识库。
     *
     * @param id 知识库 ID
     */
    void delete(KnowledgeBaseId id);
}
