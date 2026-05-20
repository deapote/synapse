package com.synapse.kb.adapter.in.web.dto;

import java.time.Instant;

public record DocumentAuditEventResponse(
        String id,
        String documentId,
        String knowledgeBaseId,
        String actorUserId,
        String action,
        String beforeSnapshot,
        String afterSnapshot,
        String reason,
        Instant createdAt
) {
}
