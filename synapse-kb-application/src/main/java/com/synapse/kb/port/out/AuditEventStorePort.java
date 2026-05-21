package com.synapse.kb.port.out;

import com.synapse.kb.model.DocumentId;
import com.synapse.kb.model.KnowledgeBaseId;

import java.time.Instant;
import java.util.List;

/**
 * 审计事件存储端口，由持久化适配器实现。
 * 保存并查询文档元数据变更与生命周期操作的审计记录。
 */
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
