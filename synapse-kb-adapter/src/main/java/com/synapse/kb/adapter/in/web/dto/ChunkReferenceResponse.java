package com.synapse.kb.adapter.in.web.dto;

import java.time.LocalDate;

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
