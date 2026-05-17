package com.synapse.kb.model;

import com.synapse.shared.exception.DomainException;

import java.util.UUID;

/** 聊天会话编号。 */
public record ChatSessionId(String value) {

    public ChatSessionId {
        if (value == null || value.isBlank()) {
            throw new DomainException("聊天会话编号不能为空");
        }
    }

    public static ChatSessionId newId() {
        return new ChatSessionId(UUID.randomUUID().toString());
    }
}
