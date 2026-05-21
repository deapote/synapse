package com.synapse.kb.adapter.out.persistence;

import com.synapse.kb.adapter.out.persistence.entity.DocumentDocument;
import com.synapse.kb.model.Document;
import com.synapse.kb.model.DocumentId;
import com.synapse.kb.model.DocumentIndexStatus;
import com.synapse.kb.model.DocumentLifecycleStatus;
import com.synapse.kb.model.DocumentSourceType;
import com.synapse.kb.model.DocumentStatus;
import com.synapse.kb.model.KnowledgeBaseId;
import com.synapse.kb.repository.DocumentQueryCriteria;
import com.synapse.kb.repository.DocumentRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/**
 * 文档 MongoDB 仓储适配器。
 */
@Component
public class MongoDocumentRepository implements DocumentRepository {

    private final DocumentMongoRepository documentMongoRepository;
    private final MongoTemplate mongoTemplate;

    public MongoDocumentRepository(DocumentMongoRepository documentMongoRepository,
                                    MongoTemplate mongoTemplate) {
        this.documentMongoRepository = documentMongoRepository;
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Document save(Document document) {
        try {
            DocumentDocument doc = toDocument(document);
            DocumentDocument saved = documentMongoRepository.save(doc);
            return toEntity(saved);
        } catch (DuplicateKeyException e) {
            throw new com.synapse.shared.exception.DomainException("此知识库已存在相同内容的文档", e);
        }
    }

    @Override
    public Optional<Document> findById(DocumentId id) {
        return documentMongoRepository.findById(id.value())
                .map(this::toEntity);
    }

    @Override
    public List<Document> findByKnowledgeBaseId(KnowledgeBaseId knowledgeBaseId) {
        return documentMongoRepository.findByKnowledgeBaseId(knowledgeBaseId.value())
                .stream()
                .map(this::toEntity)
                .toList();
    }

    @Override
    public void deleteById(DocumentId id) {
        documentMongoRepository.deleteById(id.value());
    }

    @Override
    public boolean existsByKnowledgeBaseIdAndContentHash(KnowledgeBaseId knowledgeBaseId, String contentHash) {
        return documentMongoRepository.existsByKnowledgeBaseIdAndContentHash(
                knowledgeBaseId.value(), contentHash
        );
    }

    @Override
    public List<Document> findByKnowledgeBaseId(KnowledgeBaseId knowledgeBaseId, int page, int size) {
        return documentMongoRepository.findByKnowledgeBaseId(
                        knowledgeBaseId.value(),
                        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "uploadedAt"))
                )
                .stream()
                .map(this::toEntity)
                .toList();
    }

    @Override
    public List<Document> findByKnowledgeBaseIdAndContentHash(KnowledgeBaseId knowledgeBaseId, String contentHash) {
        return documentMongoRepository.findByKnowledgeBaseIdAndContentHash(
                        knowledgeBaseId.value(), contentHash
                )
                .stream()
                .map(this::toEntity)
                .toList();
    }

    @Override
    public List<Document> findByKnowledgeBaseIdAndCanonicalKeyAndLifecycleStatus(
            KnowledgeBaseId knowledgeBaseId, String canonicalKey, DocumentLifecycleStatus status) {
        Query query = new Query(
                Criteria.where("knowledgeBaseId").is(knowledgeBaseId.value())
                        .and("canonicalKey").is(canonicalKey)
                        .and("lifecycleStatus").is(status.name())
        );
        return mongoTemplate.find(query, DocumentDocument.class).stream()
                .map(this::toEntity)
                .toList();
    }

    @Override
    public List<Document> findBySupersedesDocumentId(DocumentId documentId) {
        return documentMongoRepository.findBySupersedesDocumentId(documentId.value())
                .stream()
                .map(this::toEntity)
                .toList();
    }

    @Override
    public List<Document> findByCriteria(DocumentQueryCriteria criteria) {
        Query query = buildQuery(criteria);
        query.with(PageRequest.of(criteria.page(), criteria.size(), Sort.by(Sort.Direction.DESC, "uploadedAt")));
        return mongoTemplate.find(query, DocumentDocument.class).stream()
                .map(this::toEntity)
                .toList();
    }

    @Override
    public long countByCriteria(DocumentQueryCriteria criteria) {
        Query query = buildQuery(criteria);
        return mongoTemplate.count(query, DocumentDocument.class);
    }

    private Query buildQuery(DocumentQueryCriteria criteria) {
        Criteria c = Criteria.where("knowledgeBaseId").is(criteria.knowledgeBaseId().value());
        if (criteria.sourceType() != null) {
            c = c.and("sourceType").is(criteria.sourceType().name());
        }
        if (criteria.lifecycleStatus() != null) {
            c = c.and("lifecycleStatus").is(criteria.lifecycleStatus().name());
        }
        if (criteria.indexStatus() != null) {
            c = c.and("indexStatus").is(criteria.indexStatus().name());
        }
        if (criteria.canonicalKey() != null && !criteria.canonicalKey().isBlank()) {
            c = c.and("canonicalKey").is(criteria.canonicalKey());
        }
        return new Query(c);
    }

    private DocumentDocument toDocument(Document document) {
        DocumentDocument doc = new DocumentDocument();
        doc.setId(document.getId().value());
        doc.setKnowledgeBaseId(document.getKnowledgeBaseId().value());
        doc.setFileName(document.getFileName());
        doc.setFileType(document.getFileType());
        doc.setFileSize(document.getFileSize());
        doc.setUploadedAt(document.getUploadedAt());
        doc.setStatus(document.getStatus().name());
        doc.setFailureReason(document.getFailureReason());
        doc.setChunkCount(document.getChunkCount());
        doc.setContentHash(document.getContentHash());
        doc.setContentObjectId(document.getContentObjectId());
        doc.setProcessingStartAt(document.getProcessingStartAt());
        doc.setProcessingCompleteAt(document.getProcessingCompleteAt());
        doc.setSourceType(document.getSourceType() != null ? document.getSourceType().name() : null);
        doc.setCanonicalKey(document.getCanonicalKey());
        doc.setVersionLabel(document.getVersionLabel());
        doc.setEffectiveFrom(document.getEffectiveFrom());
        doc.setEffectiveTo(document.getEffectiveTo());
        doc.setLifecycleStatus(document.getLifecycleStatus().name());
        doc.setSupersedesDocumentId(document.getSupersedesDocumentId());
        doc.setAuthorityLevel(document.getAuthorityLevel());
        doc.setJurisdiction(document.getJurisdiction());
        doc.setMetadataVersion(document.getMetadataVersion());
        doc.setIndexedMetadataVersion(document.getIndexedMetadataVersion());
        doc.setIndexStatus(document.getIndexStatus().name());
        doc.setLastIndexRefreshAt(document.getLastIndexRefreshAt());
        doc.setLastIndexFailureReason(document.getLastIndexFailureReason());
        return doc;
    }

    private Document toEntity(DocumentDocument doc) {
        DocumentStatus status = doc.getStatus() != null
                ? DocumentStatus.valueOf(doc.getStatus())
                : DocumentStatus.PENDING;

        DocumentSourceType sourceType = doc.getSourceType() != null
                ? DocumentSourceType.valueOf(doc.getSourceType())
                : DocumentSourceType.GENERAL;

        DocumentLifecycleStatus lifecycleStatus = doc.getLifecycleStatus() != null
                ? DocumentLifecycleStatus.valueOf(doc.getLifecycleStatus())
                : DocumentLifecycleStatus.ACTIVE;

        DocumentIndexStatus indexStatus = doc.getIndexStatus() != null
                ? DocumentIndexStatus.valueOf(doc.getIndexStatus())
                : DocumentIndexStatus.SYNCED;

        LocalDate effectiveFrom = doc.getEffectiveFrom() != null
                ? doc.getEffectiveFrom()
                : LocalDate.ofInstant(doc.getUploadedAt(), ZoneId.systemDefault());

        Integer authorityLevel = doc.getAuthorityLevel() != null
                ? doc.getAuthorityLevel()
                : 0;

        return Document.reconstruct(
                new DocumentId(doc.getId()),
                new KnowledgeBaseId(doc.getKnowledgeBaseId()),
                doc.getFileName(),
                doc.getFileType(),
                doc.getFileSize(),
                doc.getContentHash(),
                doc.getUploadedAt(),
                status,
                doc.getFailureReason(),
                doc.getChunkCount(),
                doc.getContentObjectId(),
                doc.getProcessingStartAt(),
                doc.getProcessingCompleteAt(),
                sourceType,
                doc.getCanonicalKey(),
                doc.getVersionLabel(),
                effectiveFrom,
                doc.getEffectiveTo(),
                lifecycleStatus,
                doc.getSupersedesDocumentId(),
                authorityLevel,
                doc.getJurisdiction(),
                doc.getMetadataVersion(),
                doc.getIndexedMetadataVersion(),
                indexStatus,
                doc.getLastIndexRefreshAt(),
                doc.getLastIndexFailureReason()
        );
    }
}
