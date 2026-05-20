package com.synapse.kb.adapter.out.persistence;

import com.synapse.kb.adapter.out.persistence.entity.DocumentAuditEventDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface DocumentAuditEventMongoRepository extends MongoRepository<DocumentAuditEventDocument, String> {

    List<DocumentAuditEventDocument> findByDocumentIdOrderByCreatedAtDesc(String documentId);
}
