package com.synapse.kb.repository;

import com.synapse.kb.model.DocumentLifecycleStatus;
import com.synapse.kb.model.DocumentSourceType;
import com.synapse.kb.model.DocumentIndexStatus;
import com.synapse.kb.model.KnowledgeBaseId;

/**
 * 文档查询条件值对象，用于 Repository 查询参数封装。
 * 支持按知识库、来源类型、时效状态、索引状态及规范键过滤。
 */
public record DocumentQueryCriteria(
        KnowledgeBaseId knowledgeBaseId,
        int page,
        int size,
        DocumentSourceType sourceType,
        DocumentLifecycleStatus lifecycleStatus,
        DocumentIndexStatus indexStatus,
        String canonicalKey
) {
}
