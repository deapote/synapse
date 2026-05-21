package com.synapse.kb.adapter.in.web.dto;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 文档详情响应 DTO，包含完整时效与索引元数据。
 */
public record DocumentResponse(
        String id,
        String knowledgeBaseId,
        String fileName,
        String fileType,
        long fileSize,
        String status,
        int chunkCount,
        Instant uploadedAt,
        String sourceType,
        String canonicalKey,
        String versionLabel,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        String lifecycleStatus,
        String supersedesDocumentId,
        Integer authorityLevel,
        String jurisdiction,
        Long metadataVersion,
        Long indexedMetadataVersion,
        String indexStatus,
        Instant lastIndexRefreshAt,
        String lastIndexFailureReason
) {
}
