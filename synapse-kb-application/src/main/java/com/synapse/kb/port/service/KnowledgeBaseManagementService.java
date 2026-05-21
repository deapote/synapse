package com.synapse.kb.port.service;

import com.synapse.kb.model.Document;
import com.synapse.kb.model.KnowledgeBase;
import com.synapse.kb.model.KnowledgeBaseId;
import com.synapse.kb.port.in.CreateKnowledgeBaseUseCase;
import com.synapse.kb.port.in.DeleteKnowledgeBaseUseCase;
import com.synapse.kb.port.in.ListKnowledgeBaseUseCase;
import com.synapse.kb.port.out.AccessControlPort;
import com.synapse.kb.port.out.DocumentIndexRefreshJobRepository;
import com.synapse.kb.port.service.support.DocumentIndexingService;
import com.synapse.kb.port.service.support.KnowledgeBaseAccessGuard;
import com.synapse.kb.repository.DocumentChunkRepository;
import com.synapse.kb.repository.DocumentRepository;
import com.synapse.kb.repository.KnowledgeBaseRepository;
import com.synapse.kb.port.out.IngestionJobRepository;

import java.util.List;

/**
 * 知识库管理服务，编排知识库的创建、列表与删除。
 * 删除知识库时级联清理其下所有文档的索引、内容与任务。
 */
public class KnowledgeBaseManagementService implements
        CreateKnowledgeBaseUseCase,
        DeleteKnowledgeBaseUseCase,
        ListKnowledgeBaseUseCase {

    private final KnowledgeBaseAccessGuard accessGuard;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final AccessControlPort accessControlPort;
    private final DocumentRepository documentRepository;
    private final DocumentIndexingService documentIndexingService;
    private final DocumentChunkRepository documentChunkRepository;
    private final DocumentIndexRefreshJobRepository refreshJobRepository;
    private final IngestionJobRepository ingestionJobRepository;

    public KnowledgeBaseManagementService(
            KnowledgeBaseAccessGuard accessGuard,
            KnowledgeBaseRepository knowledgeBaseRepository,
            AccessControlPort accessControlPort,
            DocumentRepository documentRepository,
            DocumentIndexingService documentIndexingService,
            DocumentChunkRepository documentChunkRepository,
            DocumentIndexRefreshJobRepository refreshJobRepository,
            IngestionJobRepository ingestionJobRepository) {
        this.accessGuard = accessGuard;
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.accessControlPort = accessControlPort;
        this.documentRepository = documentRepository;
        this.documentIndexingService = documentIndexingService;
        this.documentChunkRepository = documentChunkRepository;
        this.refreshJobRepository = refreshJobRepository;
        this.ingestionJobRepository = ingestionJobRepository;
    }

    @Override
    public KnowledgeBaseId create(CreateKnowledgeBaseCommand command) {
        accessControlPort.checkPermission("KB_WRITE");
        KnowledgeBase kb = KnowledgeBase.create(command.name(), command.description(), accessControlPort.currentUserId());
        return knowledgeBaseRepository.save(kb).getId();
    }

    @Override
    public List<KnowledgeBase> listAll() {
        accessControlPort.checkPermission("KB_READ");
        return accessGuard.filterVisible(knowledgeBaseRepository.findAll());
    }

    @Override
    public List<KnowledgeBase> listAll(int page, int size) {
        accessControlPort.checkPermission("KB_READ");
        return accessGuard.filterVisible(knowledgeBaseRepository.findAll(page, size));
    }

    @Override
    public void delete(KnowledgeBaseId id) {
        KnowledgeBase kb = accessGuard.requireKnowledgeBase(id);
        accessGuard.checkKnowledgeBaseAccess(kb, "KB_DELETE");
        List<Document> documents = documentRepository.findByKnowledgeBaseId(id);
        for (Document doc : documents) {
            documentIndexingService.cleanupDocumentIndexesQuietly(doc);
            documentIndexingService.deleteContentObjectQuietly(doc);
            refreshJobRepository.deleteByDocumentId(doc.getId().value());
            ingestionJobRepository.deleteByDocumentId(doc.getId());
            documentRepository.deleteById(doc.getId());
        }
        ingestionJobRepository.deleteByKnowledgeBaseId(id);
        knowledgeBaseRepository.deleteById(id);
    }
}
