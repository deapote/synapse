package com.synapse.kb.port.in;

import com.synapse.kb.model.KnowledgeBaseId;

/** 删除知识库入站端口。 */
public interface DeleteKnowledgeBaseUseCase {

    void delete(KnowledgeBaseId id);
}
