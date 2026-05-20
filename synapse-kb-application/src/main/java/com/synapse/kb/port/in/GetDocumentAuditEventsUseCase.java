package com.synapse.kb.port.in;

import com.synapse.kb.model.DocumentId;

import java.util.List;

public interface GetDocumentAuditEventsUseCase {

    List<DocumentAuditEvent> getAuditEvents(DocumentId id);

    record DocumentAuditEvent(
            String id,
            String documentId,
            String knowledgeBaseId,
            String actorUserId,
            String action,
            String beforeSnapshot,
            String afterSnapshot,
            String reason,
            java.time.Instant createdAt
    ) {}
}
