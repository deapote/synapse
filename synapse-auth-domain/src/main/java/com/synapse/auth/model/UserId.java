package com.synapse.auth.model;

import com.synapse.shared.exception.DomainException;

import java.util.UUID;

public record UserId(String value) {
    public UserId {
        if (value == null || value.isBlank()) {
            throw new DomainException("用户ID不能为空");
        }
    }

    public static UserId generate() {
        return new UserId(UUID.randomUUID().toString());
    }
}
