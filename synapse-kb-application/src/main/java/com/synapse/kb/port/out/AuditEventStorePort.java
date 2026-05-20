package com.synapse.kb.port.out;

import com.synapse.kb.model.DocumentId;
import com.synapse.kb.model.KnowledgeBaseId;

import java.time.Instant;
import java.util.List;

public interface AuditEventStorePort {

    void save(AuditEvent event);

    List<AuditEvent> findByDocumentId(DocumentId documentId);

    record AuditEvent(
            String id,
            DocumentId documentId,
            KnowledgeBaseId knowledgeBaseId,
            String actorUserId,
            String action,
            String beforeSnapshot,
            String afterSnapshot,
            String reason,
            Instant createdAt
    ) {}
}
