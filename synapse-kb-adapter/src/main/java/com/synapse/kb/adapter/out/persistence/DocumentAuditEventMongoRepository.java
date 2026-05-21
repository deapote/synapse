package com.synapse.kb.adapter.out.persistence;

import com.synapse.kb.adapter.out.persistence.entity.DocumentAuditEventDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

/**
 * 文档审计事件 Spring Data MongoDB 仓储接口。
 */
public interface DocumentAuditEventMongoRepository extends MongoRepository<DocumentAuditEventDocument, String> {

    List<DocumentAuditEventDocument> findByDocumentIdOrderByCreatedAtDesc(String documentId);
}
