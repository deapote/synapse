package com.synapse.kb.port.service;

import com.synapse.kb.model.*;
import com.synapse.kb.port.in.DeleteDocumentUseCase;
import com.synapse.kb.port.in.IngestDocumentUseCase;
import com.synapse.kb.port.in.ListDocumentUseCase;
import com.synapse.kb.port.in.RetryDocumentIngestionUseCase;
import com.synapse.kb.port.out.AccessControlPort;
import com.synapse.kb.port.out.DocumentContentStorePort;
import com.synapse.kb.port.out.IngestionJobRepository;
import com.synapse.kb.port.service.support.DocumentIndexingService;
import com.synapse.kb.port.service.support.KnowledgeBaseAccessGuard;
import com.synapse.kb.repository.DocumentRepository;
import com.synapse.shared.exception.DomainException;

import java.util.List;

/**
 * 文档命令服务，编排文档的创建、查询、删除与重试。
 * 处理上传去重、内容存储、摄入任务提交及失败清理。
 */
public class DocumentCommandService implements
        IngestDocumentUseCase,
        ListDocumentUseCase,
        DeleteDocumentUseCase,
        RetryDocumentIngestionUseCase {

    private final KnowledgeBaseAccessGuard accessGuard;
    private final DocumentRepository documentRepository;
    private final AccessControlPort accessControlPort;
    private final DocumentContentStorePort documentContentStorePort;
    private final IngestionJobRepository ingestionJobRepository;
    private final DocumentIndexingService documentIndexingService;

    public DocumentCommandService(KnowledgeBaseAccessGuard accessGuard,
                                  DocumentRepository documentRepository,
                                  AccessControlPort accessControlPort,
                                  DocumentContentStorePort documentContentStorePort,
                                  IngestionJobRepository ingestionJobRepository,
                                  DocumentIndexingService documentIndexingService) {
        this.accessGuard = accessGuard;
        this.documentRepository = documentRepository;
        this.accessControlPort = accessControlPort;
        this.documentContentStorePort = documentContentStorePort;
        this.ingestionJobRepository = ingestionJobRepository;
        this.documentIndexingService = documentIndexingService;
    }

    @Override
    public DocumentId ingest(IngestDocumentCommand command) {
        KnowledgeBase kb = accessGuard.requireKnowledgeBase(command.knowledgeBaseId());
        accessGuard.checkKnowledgeBaseAccess(kb, "KB_WRITE");

        List<Document> existingDocs = documentRepository.findByKnowledgeBaseIdAndContentHash(
                command.knowledgeBaseId(), command.contentHash()
        );
        for (Document existing : existingDocs) {
            if (existing.getStatus() == DocumentStatus.PENDING
                    || existing.getStatus() == DocumentStatus.PROCESSING
                    || existing.getStatus() == DocumentStatus.COMPLETED) {
                throw new DomainException("此知识库已存在相同内容的文档");
            }
            if (existing.getStatus() == DocumentStatus.FAILED) {
                documentIndexingService.cleanupDocumentIndexesQuietly(existing);
                documentIndexingService.deleteContentObjectQuietly(existing);
                ingestionJobRepository.deleteByDocumentId(existing.getId());
                documentRepository.deleteById(existing.getId());
            }
        }

        DocumentMetadata metadata = command.metadata();
        if (metadata.supersedesDocumentId() != null && !metadata.supersedesDocumentId().isBlank()) {
            DocumentId oldId = new DocumentId(metadata.supersedesDocumentId());
            Document oldDoc = documentRepository.findById(oldId)
                    .orElseThrow(() -> new DomainException("被替代文档不存在: " + metadata.supersedesDocumentId()));
            if (!oldDoc.getKnowledgeBaseId().equals(command.knowledgeBaseId())) {
                throw new DomainException("被替代文档必须属于同一知识库");
            }
            if (oldDoc.getLifecycleStatus() != DocumentLifecycleStatus.ACTIVE) {
                throw new DomainException("被替代文档必须是 ACTIVE 状态");
            }
        }

        Document document = Document.create(
                command.knowledgeBaseId(),
                command.fileName(),
                command.contentType(),
                command.fileSize(),
                command.contentHash(),
                metadata
        );

        String contentObjectId = documentContentStorePort.store(
                command.knowledgeBaseId(),
                document.getId(),
                document.getFileName(),
                document.getFileType(),
                command.content()
        );
        document.attachContentObject(contentObjectId);
        try {
            document = documentRepository.save(document);
        } catch (RuntimeException e) {
            documentContentStorePort.delete(contentObjectId);
            throw e;
        }
        try {
            ingestionJobRepository.save(IngestionJob.create(document.getId(), document.getKnowledgeBaseId(), contentObjectId));
        } catch (RuntimeException e) {
            documentRepository.deleteById(document.getId());
            documentContentStorePort.delete(contentObjectId);
            throw e;
        }

        return document.getId();
    }

    @Override
    public List<Document> listByKnowledgeBase(KnowledgeBaseId knowledgeBaseId) {
        KnowledgeBase kb = accessGuard.requireKnowledgeBase(knowledgeBaseId);
        accessGuard.checkKnowledgeBaseAccess(kb, "KB_READ");
        return documentRepository.findByKnowledgeBaseId(knowledgeBaseId);
    }

    @Override
    public List<Document> listByKnowledgeBase(KnowledgeBaseId knowledgeBaseId, int page, int size) {
        KnowledgeBase kb = accessGuard.requireKnowledgeBase(knowledgeBaseId);
        accessGuard.checkKnowledgeBaseAccess(kb, "KB_READ");
        return documentRepository.findByKnowledgeBaseId(knowledgeBaseId, page, size);
    }

    @Override
    public List<Document> listDocuments(ListDocumentQuery query) {
        KnowledgeBase kb = accessGuard.requireKnowledgeBase(query.knowledgeBaseId());
        accessGuard.checkKnowledgeBaseAccess(kb, "KB_READ");
        int safePage = Math.max(0, query.page());
        int safeSize = Math.max(1, Math.min(query.size(), 100));
        com.synapse.kb.repository.DocumentQueryCriteria criteria =
                new com.synapse.kb.repository.DocumentQueryCriteria(
                        query.knowledgeBaseId(), safePage, safeSize,
                        query.sourceType(), query.lifecycleStatus(), query.indexStatus(), query.canonicalKey()
                );
        return documentRepository.findByCriteria(criteria);
    }

    @Override
    public void delete(DocumentId id) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new DomainException("未找到文档: " + id.value()));
        KnowledgeBase kb = accessGuard.requireKnowledgeBase(document.getKnowledgeBaseId());
        accessGuard.checkKnowledgeBaseAccess(kb, "KB_DELETE");
        documentIndexingService.cleanupDocumentIndexesQuietly(document);
        documentIndexingService.deleteContentObjectQuietly(document);
        ingestionJobRepository.deleteByDocumentId(id);
        documentRepository.deleteById(id);
    }

    @Override
    public Document retry(DocumentId id) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new DomainException("未找到文档: " + id.value()));
        KnowledgeBase kb = accessGuard.requireKnowledgeBase(document.getKnowledgeBaseId());
        accessGuard.checkKnowledgeBaseAccess(kb, "KB_WRITE");
        if (document.getStatus() != DocumentStatus.FAILED) {
            throw new DomainException("仅失败文档支持重试");
        }
        if (document.getContentObjectId() == null || document.getContentObjectId().isBlank()) {
            throw new DomainException("文档原始内容不存在，无法重试");
        }
        documentIndexingService.cleanupDocumentIndexesQuietly(document);
        ingestionJobRepository.deleteByDocumentId(id);
        document.retry();
        document = documentRepository.save(document);
        ingestionJobRepository.save(IngestionJob.create(document.getId(), document.getKnowledgeBaseId(), document.getContentObjectId()));
        return document;
    }
}
