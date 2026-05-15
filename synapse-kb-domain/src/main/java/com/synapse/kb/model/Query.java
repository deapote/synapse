package com.synapse.kb.model;

import com.synapse.shared.exception.DomainException;

/**
 * 用户查询值对象。
 *
 * <p>封装向知识库发起问答请求时的查询条件，包含目标知识库和查询文本。
 * 作为 {@link record} 实现，天然不可变、线程安全。
 *
 * @param knowledgeBaseId 目标知识库唯一标识，限定搜索范围，禁止跨库查询
 * @param text            用户原始查询文本
 */
public record Query(
        KnowledgeBaseId knowledgeBaseId,
        String text
) {

    /**
     * 紧凑构造方法：在编译器自动完成字段赋值前执行校验。
     */
    public Query {
        if (knowledgeBaseId == null) {
            throw new DomainException("知识库编号不能为空");
        }
        if (text == null || text.isBlank()) {
            throw new DomainException("查询文本不能为空");
        }
    }
}
