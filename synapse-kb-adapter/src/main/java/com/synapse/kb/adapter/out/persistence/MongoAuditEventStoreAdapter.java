package com.synapse.kb.adapter.out.persistence;

import com.synapse.kb.adapter.out.persistence.entity.DocumentAuditEventDocument;
import com.synapse.kb.model.DocumentId;
import com.synapse.kb.model.KnowledgeBaseId;
import com.synapse.kb.port.out.AuditEventStorePort;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MongoAuditEventStoreAdapter implements AuditEventStorePort {

    private final DocumentAuditEventMongoRepository auditEventMongoRepository;

    public MongoAuditEventStoreAdapter(DocumentAuditEventMongoRepository auditEventMongoRepository) {
        this.auditEventMongoRepository = auditEventMongoRepository;
    }

    @Override
    public void save(AuditEvent event) {
        DocumentAuditEventDocument doc = new DocumentAuditEventDocument();
        doc.setId(event.id());
        doc.setDocumentId(event.documentId().value());
        doc.setKnowledgeBaseId(event.knowledgeBaseId().value());
        doc.setActorUserId(event.actorUserId());
        doc.setAction(event.action());
        doc.setBeforeSnapshot(event.beforeSnapshot());
        doc.setAfterSnapshot(event.afterSnapshot());
        doc.setReason(event.reason());
        doc.setCreatedAt(event.createdAt());
        auditEventMongoRepository.save(doc);
    }

    @Override
    public List<AuditEvent> findByDocumentId(DocumentId documentId) {
        return auditEventMongoRepository.findByDocumentIdOrderByCreatedAtDesc(documentId.value())
                .stream()
                .map(doc -> new AuditEvent(
                        doc.getId(),
                        new DocumentId(doc.getDocumentId()),
                        new KnowledgeBaseId(doc.getKnowledgeBaseId()),
                        doc.getActorUserId(),
                        doc.getAction(),
                        doc.getBeforeSnapshot(),
                        doc.getAfterSnapshot(),
                        doc.getReason(),
                        doc.getCreatedAt()
                ))
                .toList();
    }
}
