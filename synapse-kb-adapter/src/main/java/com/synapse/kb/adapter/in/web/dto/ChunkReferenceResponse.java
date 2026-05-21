package com.synapse.kb.adapter.in.web.dto;

import java.time.LocalDate;

/**
 * 检索片段引用响应 DTO，包含文档时效元数据用于引用展示。
 */
public record ChunkReferenceResponse(
        int sourceId,
        String documentId,
        String documentName,
        String chunkText,
        float score,
        int startPosition,
        int endPosition,
        Boolean used,
        String canonicalKey,
        String versionLabel,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        String lifecycleStatus,
        int authorityLevel,
        String jurisdiction
) {
}
