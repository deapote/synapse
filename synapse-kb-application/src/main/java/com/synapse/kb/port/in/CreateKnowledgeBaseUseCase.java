package com.synapse.kb.port.in;

import com.synapse.kb.model.KnowledgeBaseId;

/** 创建知识库入站端口。 */
public interface CreateKnowledgeBaseUseCase {

    KnowledgeBaseId create(CreateKnowledgeBaseCommand command);

    record CreateKnowledgeBaseCommand(String name, String description, String ownerUserId) {
    }
}
