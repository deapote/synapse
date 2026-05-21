package com.synapse.kb.port.service.support;

import com.synapse.kb.model.Document;
import com.synapse.kb.model.DocumentId;
import com.synapse.kb.port.out.AccessControlPort;
import com.synapse.kb.port.out.AuditEventStorePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 文档审计辅助服务。
 * 封装审计事件查询与写入，失败时静默降级避免影响主流程。
 */
public class DocumentAuditService {
    private static final Logger log = LoggerFactory.getLogger(DocumentAuditService.class);
    private final AuditEventStorePort auditEventStorePort;
    private final AccessControlPort accessControlPort;

    public DocumentAuditService(AuditEventStorePort auditEventStorePort,
                                AccessControlPort accessControlPort) {
        this.auditEventStorePort = auditEventStorePort;
        this.accessControlPort = accessControlPort;
    }

    public List<AuditEventStorePort.AuditEvent> getAuditEvents(DocumentId documentId) {
        return auditEventStorePort.findByDocumentId(documentId);
    }

    public void auditEvent(Document document, String action, String before, String after, String reason) {
        try {
            auditEventStorePort.save(new AuditEventStorePort.AuditEvent(
                    UUID.randomUUID().toString(),
                    document.getId(),
                    document.getKnowledgeBaseId(),
                    accessControlPort.currentUserId(),
                    action,
                    before,
                    after,
                    reason,
                    Instant.now()
            ));
        } catch (Exception e) {
            log.warn("审计日志写入失败 documentId={} action={}", document.getId().value(), action, e);
        }
    }
}
