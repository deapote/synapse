package com.synapse.kb.adapter.out.persistence;

import com.synapse.kb.adapter.out.persistence.entity.DocumentDocument;
import com.synapse.kb.model.Document;
import com.synapse.kb.model.DocumentId;
import com.synapse.kb.model.DocumentStatus;
import com.synapse.kb.model.KnowledgeBaseId;
import com.synapse.kb.repository.DocumentRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * 文档仓储实现（MongoDB 适配器）。
 *
 * <p>实现领域层定义的 {@link DocumentRepository} 接口，将领域对象与 MongoDB 文档实体互相转换。
 * 内部使用 Spring Data Reactive MongoDB 驱动，通过 {@link #block()} 将 Reactive API 包装为同步 API。
 */
@Component
public class MongoDocumentRepository implements DocumentRepository {

    private final DocumentMongoRepository documentMongoRepository;

    public MongoDocumentRepository(DocumentMongoRepository documentMongoRepository) {
        this.documentMongoRepository = documentMongoRepository;
    }

    @Override
    public Document save(Document document) {
        DocumentDocument doc = toDocument(document);
        DocumentDocument saved = documentMongoRepository.save(doc).block();
        return toEntity(saved);
    }

    @Override
    public Optional<Document> findById(DocumentId id) {
        return documentMongoRepository.findById(id.value())
                .blockOptional()
                .map(this::toEntity);
    }

    @Override
    public List<Document> findByKnowledgeBaseId(KnowledgeBaseId knowledgeBaseId) {
        return documentMongoRepository.findByKnowledgeBaseId(knowledgeBaseId.value())
                .toStream()
                .map(this::toEntity)
                .toList();
    }

    @Override
    public void deleteById(DocumentId id) {
        documentMongoRepository.deleteById(id.value()).block();
    }

    @Override
    public boolean existsByKnowledgeBaseIdAndContentHash(KnowledgeBaseId knowledgeBaseId, String contentHash) {
        return Boolean.TRUE.equals(
                documentMongoRepository.existsByKnowledgeBaseIdAndContentHash(
                        knowledgeBaseId.value(), contentHash
                ).block()
        );
    }

    @Override
    public List<Document> findByKnowledgeBaseIdAndContentHash(KnowledgeBaseId knowledgeBaseId, String contentHash) {
        return documentMongoRepository.findByKnowledgeBaseIdAndContentHash(
                        knowledgeBaseId.value(), contentHash
                )
                .toStream()
                .map(this::toEntity)
                .toList();
    }

    /**
     * 将领域对象转换为 MongoDB 文档实体。
     */
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
        doc.setProcessingStartAt(document.getProcessingStartAt());
        doc.setProcessingCompleteAt(document.getProcessingCompleteAt());
        return doc;
    }

    /**
     * 将 MongoDB 文档实体转换为领域对象。
     */
    private Document toEntity(DocumentDocument doc) {
        DocumentStatus status = doc.getStatus() != null
                ? DocumentStatus.valueOf(doc.getStatus())
                : DocumentStatus.PENDING;

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
                doc.getProcessingStartAt(),
                doc.getProcessingCompleteAt()
        );
    }
}
