package com.synapse.kb.model;

import com.synapse.shared.exception.DomainException;

import java.util.UUID;

/** 文档唯一标识。 */
public record DocumentId(String value) {

    public DocumentId {
        if (value == null || value.isBlank()) {
            throw new DomainException("文档ID不能为空或者空白");
        }
    }

    public static DocumentId generate() {
        return new DocumentId(UUID.randomUUID().toString());
    }
}
