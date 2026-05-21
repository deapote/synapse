package com.synapse.kb.port.in;

import com.synapse.kb.model.DocumentId;

import java.util.List;

/**
 * 获取文档审计事件用例。
 * 查询指定文档的元数据变更与生命周期操作记录。
 */
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
