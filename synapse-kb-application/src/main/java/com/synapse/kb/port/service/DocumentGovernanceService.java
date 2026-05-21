package com.synapse.kb.port.service;

import com.synapse.kb.model.*;
import com.synapse.kb.port.in.*;
import com.synapse.kb.port.out.AccessControlPort;
import com.synapse.kb.port.out.AuditEventStorePort;
import com.synapse.kb.port.out.DocumentIndexRefreshJobRepository;
import com.synapse.kb.port.service.support.DocumentAuditService;
import com.synapse.kb.port.service.support.KnowledgeBaseAccessGuard;
import com.synapse.kb.port.service.support.MetadataSnapshotter;
import com.synapse.kb.repository.DocumentRepository;
import com.synapse.shared.exception.DomainException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

/**
 * 文档治理服务，编排文档元数据与生命周期管理。
 * 负责更新元数据、替代、退役、重新激活、重建索引及版本链查询。
 */
public class DocumentGovernanceService implements
        UpdateDocumentMetadataUseCase,
        SupersedeDocumentUseCase,
        RetireDocumentUseCase,
        ReactivateDocumentUseCase,
        ReindexDocumentUseCase,
        GetDocumentVersionChainUseCase,
        GetDocumentAuditEventsUseCase {

    private final KnowledgeBaseAccessGuard accessGuard;
    private final DocumentRepository documentRepository;
    private final AccessControlPort accessControlPort;
    private final DocumentIndexRefreshJobRepository refreshJobRepository;
    private final MetadataSnapshotter metadataSnapshotter;
    private final DocumentAuditService documentAuditService;

    public DocumentGovernanceService(KnowledgeBaseAccessGuard accessGuard,
                                     DocumentRepository documentRepository,
                                     AccessControlPort accessControlPort,
                                     DocumentIndexRefreshJobRepository refreshJobRepository,
                                     MetadataSnapshotter metadataSnapshotter,
                                     DocumentAuditService documentAuditService) {
        this.accessGuard = accessGuard;
        this.documentRepository = documentRepository;
        this.accessControlPort = accessControlPort;
        this.refreshJobRepository = refreshJobRepository;
        this.metadataSnapshotter = metadataSnapshotter;
        this.documentAuditService = documentAuditService;
    }

    @Override
    public Document updateMetadata(DocumentId id, PatchDocumentMetadata patch) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new DomainException("未找到文档: " + id.value()));
        accessGuard.requireKnowledgeBase(document.getKnowledgeBaseId());
        accessGuard.checkKnowledgeBaseAccess(accessGuard.requireKnowledgeBase(document.getKnowledgeBaseId()), "KB_WRITE");

        String before = metadataSnapshotter.snapshotMetadata(document);
        document.patchMetadata(patch);
        document.markIndexStale();
        document = documentRepository.save(document);
        createIndexRefreshJob(document);
        documentAuditService.auditEvent(document, "METADATA_UPDATED", before, metadataSnapshotter.snapshotMetadata(document), null);
        return document;
    }

    @Override
    public void supersede(DocumentId oldDocumentId, DocumentId newDocumentId, LocalDate effectiveTo) {
        Document oldDoc = documentRepository.findById(oldDocumentId)
                .orElseThrow(() -> new DomainException("未找到旧文档: " + oldDocumentId.value()));
        Document newDoc = documentRepository.findById(newDocumentId)
                .orElseThrow(() -> new DomainException("未找到新文档: " + newDocumentId.value()));
        accessGuard.checkKnowledgeBaseAccess(accessGuard.requireKnowledgeBase(oldDoc.getKnowledgeBaseId()), "KB_WRITE");

        if (!oldDoc.getKnowledgeBaseId().equals(newDoc.getKnowledgeBaseId())) {
            throw new DomainException("被替代文档与替代文档必须属于同一知识库");
        }
        if (oldDoc.getLifecycleStatus() != DocumentLifecycleStatus.ACTIVE) {
            throw new DomainException("仅 ACTIVE 文档可被替代");
        }
        if (newDoc.getStatus() != DocumentStatus.COMPLETED) {
            throw new DomainException("替代文档必须已完成摄入");
        }

        String before = metadataSnapshotter.snapshotMetadata(oldDoc);
        oldDoc.supersedeBy(newDoc, effectiveTo);
        oldDoc.markIndexStale();
        oldDoc = documentRepository.save(oldDoc);
        createIndexRefreshJob(oldDoc);
        documentAuditService.auditEvent(oldDoc, "SUPERSEDED", before, metadataSnapshotter.snapshotMetadata(oldDoc),
                "newDocumentId=" + newDocumentId.value());
    }

    @Override
    public Document retire(DocumentId id, LocalDate effectiveTo) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new DomainException("未找到文档: " + id.value()));
        accessGuard.checkKnowledgeBaseAccess(accessGuard.requireKnowledgeBase(document.getKnowledgeBaseId()), "KB_WRITE");

        String before = metadataSnapshotter.snapshotMetadata(document);
        document.retire(effectiveTo);
        document.markIndexStale();
        document = documentRepository.save(document);
        createIndexRefreshJob(document);
        documentAuditService.auditEvent(document, "RETIRED", before, metadataSnapshotter.snapshotMetadata(document), null);
        return document;
    }

    @Override
    public Document reactivate(DocumentId id) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new DomainException("未找到文档: " + id.value()));
        accessGuard.checkKnowledgeBaseAccess(accessGuard.requireKnowledgeBase(document.getKnowledgeBaseId()), "KB_WRITE");

        String before = metadataSnapshotter.snapshotMetadata(document);
        document.reactivate();
        document.markIndexStale();
        document = documentRepository.save(document);
        createIndexRefreshJob(document);
        documentAuditService.auditEvent(document, "REACTIVATED", before, metadataSnapshotter.snapshotMetadata(document), null);
        return document;
    }

    @Override
    public Document reindex(DocumentId id) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new DomainException("未找到文档: " + id.value()));
        accessGuard.checkKnowledgeBaseAccess(accessGuard.requireKnowledgeBase(document.getKnowledgeBaseId()), "KB_WRITE");

        document.markIndexStale();
        document = documentRepository.save(document);
        createIndexRefreshJob(document);
        documentAuditService.auditEvent(document, "INDEX_REFRESH_REQUESTED", metadataSnapshotter.snapshotMetadata(document), metadataSnapshotter.snapshotMetadata(document), null);
        return document;
    }

    @Override
    public List<Document> getVersionChain(DocumentId id) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new DomainException("未找到文档: " + id.value()));
        accessGuard.checkKnowledgeBaseAccess(accessGuard.requireKnowledgeBase(document.getKnowledgeBaseId()), "KB_READ");

        Set<String> seen = new HashSet<>();
        List<Document> chain = new ArrayList<>();

        Document current = document;
        while (current.getSupersedesDocumentId() != null && !current.getSupersedesDocumentId().isBlank()) {
            Optional<Document> prev = documentRepository.findById(new DocumentId(current.getSupersedesDocumentId()));
            if (prev.isEmpty()) {
                break;
            }
            current = prev.get();
            if (seen.add(current.getId().value())) {
                chain.addFirst(current);
            } else {
                break;
            }
        }

        if (seen.add(document.getId().value())) {
            chain.add(document);
        }

        current = document;
        while (true) {
            List<Document> nextDocs = documentRepository.findBySupersedesDocumentId(current.getId())
                    .stream()
                    .filter(d -> d.getKnowledgeBaseId().equals(document.getKnowledgeBaseId()))
                    .toList();
            if (nextDocs.isEmpty()) {
                break;
            }
            Document next = nextDocs.getFirst();
            if (!seen.add(next.getId().value())) {
                break;
            }
            chain.add(next);
            current = next;
        }

        if (document.getCanonicalKey() != null && !document.getCanonicalKey().isBlank()) {
            for (DocumentLifecycleStatus status : DocumentLifecycleStatus.values()) {
                List<Document> siblings = documentRepository.findByKnowledgeBaseIdAndCanonicalKeyAndLifecycleStatus(
                        document.getKnowledgeBaseId(), document.getCanonicalKey(), status);
                for (Document sib : siblings) {
                    if (seen.add(sib.getId().value())) {
                        chain.add(sib);
                    }
                }
            }
        }

        return chain;
    }

    @Override
    public List<GetDocumentAuditEventsUseCase.DocumentAuditEvent> getAuditEvents(DocumentId id) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new DomainException("未找到文档: " + id.value()));
        accessGuard.checkKnowledgeBaseAccess(accessGuard.requireKnowledgeBase(document.getKnowledgeBaseId()), "KB_READ");

        return documentAuditService.getAuditEvents(id).stream()
                .map(e -> new GetDocumentAuditEventsUseCase.DocumentAuditEvent(
                        e.id(), e.documentId().value(), e.knowledgeBaseId().value(),
                        e.actorUserId(), e.action(), e.beforeSnapshot(), e.afterSnapshot(),
                        e.reason(), e.createdAt()
                ))
                .toList();
    }

    private void createIndexRefreshJob(Document document) {
        var job = DocumentIndexRefreshJob.create(
                document.getId(),
                document.getKnowledgeBaseId(),
                document.getMetadataVersion()
        );
        refreshJobRepository.save(job);
    }
}
