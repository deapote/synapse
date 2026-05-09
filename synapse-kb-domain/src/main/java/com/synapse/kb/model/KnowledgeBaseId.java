package com.synapse.kb.model;

import com.synapse.shared.exception.DomainException;

import java.util.UUID;

/**
 * 知识库唯一标识符，不可变值对象，基于 UUID 生成
 *
 * @param value
 */
public record KnowledgeBaseId(String value) {

    /**
     * 紧凑构造方法:在字段赋值前进行校验
     *
     * @throws DomainException 当 value 为 null 或者空白时
     */
    public KnowledgeBaseId {
        if (value == null || value.isBlank()) {
            throw new DomainException("知识库标识不能为空或者空白");
        }
    }

    /**
     * 生成新的随机知识库标识
     *
     * @return 基于 UUID 的 KnowledgeBaseId 实例
     */
    public static KnowledgeBaseId generate() {
        return new KnowledgeBaseId(UUID.randomUUID().toString());
    }

}
