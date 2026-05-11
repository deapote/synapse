package com.synapse.kb.model;

import com.synapse.shared.exception.DomainException;

import java.time.Instant;

/**
 * 知识库聚合根。
 *
 * <p>知识库是文档的归属容器，本身仅维护元数据（名称、描述）。
 * 文档作为独立聚合根通过 {@code knowledgeBaseId} 与之关联，避免大聚合性能问题。
 *
 * <p>不可变设计：创建后字段不可修改，线程安全。
 */
public class KnowledgeBase {

    private final KnowledgeBaseId id;
    private final String name;
    private final String description;
    private final Instant createdAt;

    private KnowledgeBase(KnowledgeBaseId id, String name, String description, Instant createdAt) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.createdAt = createdAt;
    }

    /**
     * 创建新的知识库。
     *
     * @param name        名称，必填，长度 1–200
     * @param description 描述，可为空
     * @return 新的知识库实例
     * @throws DomainException 名称非法时抛出
     */
    public static KnowledgeBase create(String name, String description) {
        if (name == null || name.isBlank()) {
            throw new DomainException("知识库名称不能为空");
        }
        if (name.length() > 200) {
            throw new DomainException("知识库名称不能超过200个字符");
        }
        return new KnowledgeBase(KnowledgeBaseId.generate(), name, description, Instant.now());
    }

    /**
     * 仅供仓储层重建聚合根使用。业务代码不应直接调用。
     *
     * <p>从持久化存储（如 MongoDB）读取数据后，通过此方法还原领域对象，
     * 保留原有的 ID 和时间戳，不触发新的生成逻辑。
     *
     * @param id          知识库唯一标识
     * @param name        名称
     * @param description 描述
     * @param createdAt   创建时间
     * @return 重建后的知识库实例
     */
    public static KnowledgeBase reconstruct(KnowledgeBaseId id, String name, String description, Instant createdAt) {
        return new KnowledgeBase(id, name, description, createdAt);
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

    public Instant getCreatedAt() {
        return createdAt;
    }
}
