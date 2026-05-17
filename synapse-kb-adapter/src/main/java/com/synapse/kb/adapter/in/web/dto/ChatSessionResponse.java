package com.synapse.kb.adapter.in.web.dto;

import java.time.Instant;

public record ChatSessionResponse(
        String id,
        String knowledgeBaseId,
        String title,
        String summary,
        long messageCount,
        Instant createdAt,
        Instant updatedAt
) {
}
