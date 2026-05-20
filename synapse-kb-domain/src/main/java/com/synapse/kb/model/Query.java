package com.synapse.kb.model;

import com.synapse.shared.exception.DomainException;

import java.time.LocalDate;

/** 单次知识库查询；knowledgeBaseId 是检索隔离边界。 */
public record Query(
        KnowledgeBaseId knowledgeBaseId,
        String text,
        String sessionId,
        LocalDate asOfDate,
        DocumentSourceType sourceType,
        String jurisdiction
) {

    public Query {
        if (knowledgeBaseId == null) {
            throw new DomainException("知识库编号不能为空");
        }
        if (text == null || text.isBlank()) {
            throw new DomainException("查询文本不能为空");
        }
        sessionId = sessionId == null || sessionId.isBlank() ? null : sessionId.strip();
        jurisdiction = jurisdiction == null || jurisdiction.isBlank() ? null : jurisdiction.strip();
    }

    public Query(KnowledgeBaseId knowledgeBaseId, String text) {
        this(knowledgeBaseId, text, null, null, null, null);
    }

    public Query(KnowledgeBaseId knowledgeBaseId, String text, String sessionId) {
        this(knowledgeBaseId, text, sessionId, null, null, null);
    }
}
