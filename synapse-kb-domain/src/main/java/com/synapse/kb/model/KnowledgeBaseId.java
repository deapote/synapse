package com.synapse.kb.model;

import com.synapse.shared.exception.DomainException;

import java.util.UUID;

/** 知识库唯一标识。 */
public record KnowledgeBaseId(String value) {

    public KnowledgeBaseId {
        if (value == null || value.isBlank()) {
            throw new DomainException("知识库标识不能为空或者空白");
        }
    }

    public static KnowledgeBaseId generate() {
        return new KnowledgeBaseId(UUID.randomUUID().toString());
    }

}
