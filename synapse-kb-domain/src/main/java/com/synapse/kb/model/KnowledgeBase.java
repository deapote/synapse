package com.synapse.kb.model;

import com.synapse.shared.exception.DomainException;

import java.time.Instant;

/**
 * 知识库聚合根，维护元数据和归属用户。
 */
public class KnowledgeBase {

    private final KnowledgeBaseId id;
    private final String name;
    private final String description;
    private final String ownerUserId;
    private final Instant createdAt;

    private KnowledgeBase(KnowledgeBaseId id, String name, String description, String ownerUserId, Instant createdAt) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.ownerUserId = ownerUserId;
        this.createdAt = createdAt;
    }

    public static KnowledgeBase create(String name, String description, String ownerUserId) {
        if (name == null || name.isBlank()) {
            throw new DomainException("知识库名称不能为空");
        }
        if (name.length() > 200) {
            throw new DomainException("知识库名称不能超过200个字符");
        }
        if (ownerUserId == null || ownerUserId.isBlank()) {
            throw new DomainException("知识库归属用户不能为空");
        }
        return new KnowledgeBase(KnowledgeBaseId.generate(), name, description, ownerUserId, Instant.now());
    }

    /** 仅供仓储层重建聚合根使用。 */
    public static KnowledgeBase reconstruct(KnowledgeBaseId id, String name, String description, String ownerUserId, Instant createdAt) {
        return new KnowledgeBase(id, name, description, ownerUserId, createdAt);
    }

    public KnowledgeBaseId getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getOwnerUserId() {
        return ownerUserId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
