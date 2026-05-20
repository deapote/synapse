package com.synapse.kb.adapter.in.web.dto;

import java.time.Instant;
import java.time.LocalDate;

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
