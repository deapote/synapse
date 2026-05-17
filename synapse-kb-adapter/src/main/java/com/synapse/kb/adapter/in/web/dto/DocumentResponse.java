package com.synapse.kb.adapter.in.web.dto;

import java.time.Instant;

public record DocumentResponse(
        String id, String knowledgeBaseId, String fileName, String fileType,
        long fileSize, String status, int chunkCount, Instant uploadedAt
) {
}
