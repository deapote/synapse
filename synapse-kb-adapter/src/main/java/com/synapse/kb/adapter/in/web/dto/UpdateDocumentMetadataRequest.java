package com.synapse.kb.adapter.in.web.dto;

import java.time.LocalDate;

public record UpdateDocumentMetadataRequest(
        String sourceType,
        String canonicalKey,
        String versionLabel,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        String supersedesDocumentId,
        Integer authorityLevel,
        String jurisdiction
) {
}
