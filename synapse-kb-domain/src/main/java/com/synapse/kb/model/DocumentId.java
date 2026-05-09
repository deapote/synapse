package com.synapse.kb.model;

import com.synapse.shared.exception.DomainException;

import java.util.UUID;

/**
 * 文档唯一标识符，不可变值对象，基于 UUID 生成
 *
 * @param value
 */
public record DocumentId(String value) {

    /**
     * 紧凑构造方法:在字段赋值前进行校验
     *
     * @throws DomainException 当 value 为 null 或者空白时
     */
    public DocumentId {
        if (value == null || value.isBlank()) {
            throw new DomainException("文档ID不能为空或者空白");
        }
    }

    /**
     * 生成新的随机文档标识
     *
     * @return 基于 UUID 的 KnowledgeBaseId 实例
     */
    public static DocumentId generate(String id) {
        return new DocumentId(UUID.randomUUID().toString());
    }
}
