package com.synapse.kb.adapter.out.persistence;

import com.synapse.kb.adapter.out.persistence.entity.DocumentDocument;
import com.synapse.kb.model.Document;
import com.synapse.kb.model.DocumentId;
import com.synapse.kb.model.DocumentStatus;
import com.synapse.kb.model.KnowledgeBaseId;
import com.synapse.kb.repository.DocumentRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * 文档 MongoDB 仓储适配器。
 */
@Component
public class MongoDocumentRepository implements DocumentRepository {

    private final DocumentMongoRepository documentMongoRepository;

    public MongoDocumentRepository(DocumentMongoRepository documentMongoRepository) {
        this.documentMongoRepository = documentMongoRepository;
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
