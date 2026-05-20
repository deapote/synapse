package com.synapse.kb.port.service;

import com.synapse.kb.model.*;
import com.synapse.kb.port.in.*;
import com.synapse.kb.port.out.*;
import com.synapse.kb.repository.ChatMessageRepository;
import com.synapse.kb.repository.DocumentChunkRepository;
import com.synapse.kb.repository.ChatSessionRepository;
import com.synapse.kb.repository.DocumentRepository;
import com.synapse.kb.repository.KnowledgeBaseRepository;
import com.synapse.kb.service.RecursiveChunkingStrategy;
import com.synapse.shared.exception.DomainException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

/**
 * 知识库用例编排器。应用层保持同步 API，外部技术能力全部通过端口注入。
 */
public class KnowledgeBaseApplicationService implements
        CreateKnowledgeBaseUseCase,
        DeleteKnowledgeBaseUseCase,
        ListKnowledgeBaseUseCase,
        IngestDocumentUseCase,
        ListDocumentUseCase,
        DeleteDocumentUseCase,
        RetryDocumentIngestionUseCase,
        ProcessIngestionJobUseCase,
        QueryKnowledgeBaseUseCase,
        GetCurrentChatSessionUseCase,
        CreateChatSessionUseCase,
        ListChatMessagesUseCase,
        UpdateDocumentMetadataUseCase,
        SupersedeDocumentUseCase,
        ProcessDocumentIndexRefreshJobUseCase,
        RetireDocumentUseCase,
        ReactivateDocumentUseCase,
        ReindexDocumentUseCase,
        GetDocumentVersionChainUseCase,
        GetDocumentAuditEventsUseCase {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseApplicationService.class);
    private static final int MAX_FAILURE_REASON_LENGTH = 500;

    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final DocumentRepository documentRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final DocumentParserPort documentParserPort;
    private final RecursiveChunkingStrategy recursiveChunkingStrategy;
    private final DocumentContentStorePort documentContentStorePort;
    private final IngestionJobRepository ingestionJobRepository;
    private final EmbeddingPort embeddingPort;
    private final VectorStorePort vectorStorePort;
    private final ChunkSearchIndexPort chunkSearchIndexPort;
    private final DocumentChunkRepository documentChunkRepository;
    private final com.synapse.kb.port.out.DocumentIndexRefreshJobRepository refreshJobRepository;
    private final QueryRewritePort queryRewritePort;
    private final ChatMemorySummarizerPort chatMemorySummarizerPort;
    private final AuditEventStorePort auditEventStorePort;
    private final AccessControlPort accessControlPort;
    private final Executor retrievalExecutor;

    /** 模板必须包含两个 {@code %s} 占位符：检索上下文、用户问题。 */
    private final String promptTemplate;
    private final int topK;
    private final int vectorCandidateK;
    private final int keywordCandidateK;
    private final double vectorWeight;
    private final double keywordWeight;
    private final boolean queryRewriteEnabled;
    private final double queryRewriteSimilarityThreshold;
    private final boolean chatMemoryEnabled;
    private final int recentMessageLimit;
    private final int summaryTriggerMessageCount;
    private final int maxSummaryChars;
    private final int ingestionMaxAttempts;
    private final Duration ingestionLeaseDuration;

    public KnowledgeBaseApplicationService(
            KnowledgeBaseRepository knowledgeBaseRepository,
            DocumentRepository documentRepository,
            com.synapse.kb.repository.ChatSessionRepository chatSessionRepository,
            com.synapse.kb.repository.ChatMessageRepository chatMessageRepository,
            DocumentParserPort documentParserPort,
            RecursiveChunkingStrategy recursiveChunkingStrategy,
            DocumentContentStorePort documentContentStorePort,
            IngestionJobRepository ingestionJobRepository,
            EmbeddingPort embeddingPort,
            VectorStorePort vectorStorePort,
            ChunkSearchIndexPort chunkSearchIndexPort,
            DocumentChunkRepository documentChunkRepository,
            com.synapse.kb.port.out.DocumentIndexRefreshJobRepository refreshJobRepository,
            QueryRewritePort queryRewritePort,
            ChatMemorySummarizerPort chatMemorySummarizerPort,
            AuditEventStorePort auditEventStorePort,
            AccessControlPort accessControlPort,
            Executor retrievalExecutor,
            String promptTemplate,
            int topK,
            int vectorCandidateK,
            int keywordCandidateK,
            double vectorWeight,
            double keywordWeight,
            boolean queryRewriteEnabled,
            double queryRewriteSimilarityThreshold,
            boolean chatMemoryEnabled,
            int recentMessageLimit,
            int summaryTriggerMessageCount,
            int maxSummaryChars,
            int ingestionMaxAttempts,
            Duration ingestionLeaseDuration
    ) {
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.documentRepository = documentRepository;
        this.chatSessionRepository = chatSessionRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.documentParserPort = documentParserPort;
        this.recursiveChunkingStrategy = recursiveChunkingStrategy;
        this.documentContentStorePort = documentContentStorePort;
        this.ingestionJobRepository = ingestionJobRepository;
        this.embeddingPort = embeddingPort;
        this.vectorStorePort = vectorStorePort;
        this.chunkSearchIndexPort = chunkSearchIndexPort;
        this.documentChunkRepository = documentChunkRepository;
        this.refreshJobRepository = refreshJobRepository;
        this.queryRewritePort = queryRewritePort;
        this.chatMemorySummarizerPort = chatMemorySummarizerPort;
        this.auditEventStorePort = auditEventStorePort;
        this.accessControlPort = accessControlPort;
        this.retrievalExecutor = retrievalExecutor;
        this.promptTemplate = promptTemplate;
        this.topK = Math.max(1, Math.min(topK, 20));
        this.vectorCandidateK = Math.max(this.topK, Math.min(vectorCandidateK, 100));
        this.keywordCandidateK = Math.max(this.topK, Math.min(keywordCandidateK, 100));
        double safeVectorWeight = Math.max(0, vectorWeight);
        double safeKeywordWeight = Math.max(0, keywordWeight);
        double totalWeight = safeVectorWeight + safeKeywordWeight;
        if (totalWeight == 0) {
            safeVectorWeight = 1;
            totalWeight = 1;
        }
        this.vectorWeight = safeVectorWeight / totalWeight;
        this.keywordWeight = safeKeywordWeight / totalWeight;
        this.queryRewriteEnabled = queryRewriteEnabled;
        this.queryRewriteSimilarityThreshold = Math.max(0, Math.min(queryRewriteSimilarityThreshold, 1));
        this.chatMemoryEnabled = chatMemoryEnabled;
        this.recentMessageLimit = Math.max(0, Math.min(recentMessageLimit, 50));
        this.summaryTriggerMessageCount = Math.max(1, summaryTriggerMessageCount);
        this.maxSummaryChars = Math.max(200, maxSummaryChars);
        this.ingestionMaxAttempts = Math.max(1, ingestionMaxAttempts);
        this.ingestionLeaseDuration = ingestionLeaseDuration == null ? Duration.ofMinutes(5) : ingestionLeaseDuration;
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
        return filterVisible(knowledgeBaseRepository.findAll());
    }

    @Override
    public List<KnowledgeBase> listAll(int page, int size) {
        accessControlPort.checkPermission("KB_READ");
        return filterVisible(knowledgeBaseRepository.findAll(page, size));
    }

    @Override
    public void delete(KnowledgeBaseId id) {
        KnowledgeBase kb = requireKnowledgeBase(id);
        accessControlPort.checkKnowledgeBaseAccess(kb, "KB_DELETE");
        List<Document> documents = documentRepository.findByKnowledgeBaseId(id);
        for (Document doc : documents) {
            vectorStorePort.deleteByDocumentId(id, doc.getId());
            chunkSearchIndexPort.deleteByDocumentId(id, doc.getId());
            documentChunkRepository.deleteByDocumentId(doc.getId());
            refreshJobRepository.deleteByDocumentId(doc.getId().value());
            deleteContentObjectQuietly(doc);
            ingestionJobRepository.deleteByDocumentId(doc.getId());
            documentRepository.deleteById(doc.getId());
        }
        ingestionJobRepository.deleteByKnowledgeBaseId(id);
        knowledgeBaseRepository.deleteById(id);
    }

    @Override
    public void delete(DocumentId id) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new DomainException("未找到文档: " + id.value()));
        KnowledgeBase kb = requireKnowledgeBase(document.getKnowledgeBaseId());
        accessControlPort.checkKnowledgeBaseAccess(kb, "KB_DELETE");
        vectorStorePort.deleteByDocumentId(document.getKnowledgeBaseId(), id);
        chunkSearchIndexPort.deleteByDocumentId(document.getKnowledgeBaseId(), id);
        documentChunkRepository.deleteByDocumentId(id);
        refreshJobRepository.deleteByDocumentId(id.value());
        deleteContentObjectQuietly(document);
        ingestionJobRepository.deleteByDocumentId(id);
        documentRepository.deleteById(id);
    }

    @Override
    public List<Document> listByKnowledgeBase(KnowledgeBaseId knowledgeBaseId) {
        KnowledgeBase kb = requireKnowledgeBase(knowledgeBaseId);
        accessControlPort.checkKnowledgeBaseAccess(kb, "KB_READ");
        return documentRepository.findByKnowledgeBaseId(knowledgeBaseId);
    }

    @Override
    public List<Document> listByKnowledgeBase(KnowledgeBaseId knowledgeBaseId, int page, int size) {
        KnowledgeBase kb = requireKnowledgeBase(knowledgeBaseId);
        accessControlPort.checkKnowledgeBaseAccess(kb, "KB_READ");
        return documentRepository.findByKnowledgeBaseId(knowledgeBaseId, page, size);
    }

    @Override
    public DocumentId ingest(IngestDocumentCommand command) {
        KnowledgeBase kb = requireKnowledgeBase(command.knowledgeBaseId());
        accessControlPort.checkKnowledgeBaseAccess(kb, "KB_WRITE");

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
                vectorStorePort.deleteByDocumentId(existing.getKnowledgeBaseId(), existing.getId());
                chunkSearchIndexPort.deleteByDocumentId(existing.getKnowledgeBaseId(), existing.getId());
                deleteContentObjectQuietly(existing);
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
    public Document retry(DocumentId id) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new DomainException("未找到文档: " + id.value()));
        KnowledgeBase kb = requireKnowledgeBase(document.getKnowledgeBaseId());
        accessControlPort.checkKnowledgeBaseAccess(kb, "KB_WRITE");
        if (document.getStatus() != DocumentStatus.FAILED) {
            throw new DomainException("仅失败文档支持重试");
        }
        if (document.getContentObjectId() == null || document.getContentObjectId().isBlank()) {
            throw new DomainException("文档原始内容不存在，无法重试");
        }
        vectorStorePort.deleteByDocumentId(document.getKnowledgeBaseId(), document.getId());
        chunkSearchIndexPort.deleteByDocumentId(document.getKnowledgeBaseId(), document.getId());
        documentChunkRepository.deleteByDocumentId(document.getId());
        ingestionJobRepository.deleteByDocumentId(document.getId());
        document.retry();
        document = documentRepository.save(document);
        ingestionJobRepository.save(IngestionJob.create(document.getId(), document.getKnowledgeBaseId(), document.getContentObjectId()));
        return document;
    }

    @Override
    public boolean processNextAvailable(String workerId) {
        IngestionJob job = ingestionJobRepository.claimNext(workerId, ingestionLeaseDuration).orElse(null);
        if (job == null) {
            return false;
        }
        runIngestionTask(job);
        return true;
    }

    @Override
    public boolean processNextRefreshJob(String workerId) {
        var jobOpt = refreshJobRepository.claimNext(workerId);
        if (jobOpt.isEmpty()) {
            return false;
        }
        runIndexRefreshTask(jobOpt.get());
        return true;
    }

    private void runIndexRefreshTask(com.synapse.kb.model.DocumentIndexRefreshJob job) {
        Document document = documentRepository.findById(job.getDocumentId()).orElse(null);
        if (document == null) {
            job.markFailed("文档不存在", Instant.now().plus(Duration.ofMinutes(1)));
            refreshJobRepository.save(job);
            return;
        }

        if (document.getMetadataVersion() != job.getMetadataVersion()) {
            job.markSucceeded();
            refreshJobRepository.save(job);
            log.info("索引刷新任务跳过旧版本 jobId={} documentId={} jobVersion={} docVersion={}",
                    job.getId().value(), document.getId().value(), job.getMetadataVersion(), document.getMetadataVersion());
            return;
        }

        try {
            document.markIndexRefreshing();
            documentRepository.save(document);

            List<DocumentChunk> chunks = documentChunkRepository.findByDocumentId(document.getId());
            if (chunks.isEmpty()) {
                throw new DomainException("文档分块不存在，无法重建索引");
            }

            List<float[]> embeddings = embeddingPort.embed(
                    chunks.stream().map(DocumentChunk::text).toList()
            );

            DocumentMetadata metadata = toMetadata(document);

            // 两阶段刷新：先写入新索引，成功后再清理旧索引。
            // Milvus 目前无法按 generation 选择性删除旧行，暂以“写入不删旧”为底线，
            // 旧行会在检索阶段因 metadata 不匹配被过滤掉（TODO: 后续引入 generation/alias 机制做物理清理）。
            vectorStorePort.store(
                    document.getKnowledgeBaseId(),
                    document.getId(),
                    document.getFileName(),
                    chunks,
                    embeddings,
                    metadata
            );
            chunkSearchIndexPort.refreshStore(
                    document.getKnowledgeBaseId(),
                    document.getId(),
                    document.getFileName(),
                    chunks,
                    metadata
            );

            document.markIndexSynced();
            documentRepository.save(document);
            job.markSucceeded();
            refreshJobRepository.save(job);
            log.info("索引刷新完成 jobId={} documentId={} chunks={}",
                    job.getId().value(), document.getId().value(), chunks.size());
        } catch (Exception e) {
            String reason = safeFailureReason(e);
            document.markIndexFailed(reason);
            documentRepository.save(document);
            job.markFailed(reason, Instant.now().plus(Duration.ofMinutes(1)));
            refreshJobRepository.save(job);
            log.warn("索引刷新失败 jobId={} documentId={} reason={}",
                    job.getId().value(), document.getId().value(), reason, e);
        }
    }

    /**
     * 执行单条摄入任务。摄入成功后会尝试替代旧版本（如有），替代失败只记录日志，
     * 不影响新文档的稳定状态。
     */
    private void runIngestionTask(IngestionJob job) {
        Document document = documentRepository.findById(job.getDocumentId()).orElse(null);
        if (document == null) {
            job.markFailed("文档不存在");
            ingestionJobRepository.save(job);
            return;
        }
        try {
            processDocument(document, job.getContentObjectId());
            job.markSucceeded();
            ingestionJobRepository.save(job);
            documentContentStorePort.delete(job.getContentObjectId());
            log.info("文档摄入完成 documentId={} knowledgeBaseId={} chunks={}",
                    document.getId().value(), document.getKnowledgeBaseId().value(), document.getChunkCount());
        } catch (DomainException e) {
            handleIngestionFailure(job, document, e);
            log.warn("文档摄入失败 documentId={} knowledgeBaseId={} reason={}",
                    document.getId().value(), document.getKnowledgeBaseId().value(), e.getMessage());
        } catch (Exception e) {
            handleIngestionFailure(job, document, e);
            log.error("文档摄入出现未预期异常 documentId={} knowledgeBaseId={}",
                    document.getId().value(), document.getKnowledgeBaseId().value(), e);
        }
    }

    /**
     * 处理摄入失败。未达最大重试次数时标记为 RETRYING 并设置退避时间；
     * 否则将文档和任务均标记为最终失败。
     */
    private void handleIngestionFailure(IngestionJob job, Document document, Exception e) {
        String reason = safeFailureReason(e);
        if (job.getAttempts() >= ingestionMaxAttempts) {
            tryTransitionToFailed(document, reason);
            documentRepository.save(document);
            job.markFailed(reason);
        } else {
            job.markRetrying(reason, Instant.now().plus(backoffForAttempt(job.getAttempts())));
        }
        ingestionJobRepository.save(job);
    }

    private Duration backoffForAttempt(int attempt) {
        return attempt <= 1 ? Duration.ofSeconds(30) : Duration.ofMinutes(2);
    }

    private void tryTransitionToFailed(Document document, String reason) {
        if (document.getStatus() == DocumentStatus.PROCESSING) {
            document.transitionTo(DocumentStatus.FAILED, reason);
        } else if (document.getStatus() == DocumentStatus.PENDING) {
            document.transitionTo(DocumentStatus.PROCESSING);
            document.transitionTo(DocumentStatus.FAILED, reason);
        }
    }

    private void processDocument(Document document, String contentObjectId) {
        try {
            if (document.getStatus() == DocumentStatus.PENDING) {
                document.transitionTo(DocumentStatus.PROCESSING);
                documentRepository.save(document);
            }
            log.info("文档摄入开始 documentId={} knowledgeBaseId={}",
                    document.getId().value(), document.getKnowledgeBaseId().value());

            List<DocumentChunk> chunks = parseDocumentChunks(document, contentObjectId);
            storeDocumentIndexes(document, chunks);
            documentChunkRepository.save(document.getId(), chunks);

            document.setChunkCount(chunks.size());
            document.transitionTo(DocumentStatus.COMPLETED);

        } catch (Exception e) {
            cleanupDocumentIndexesQuietly(document);
            throw new DomainException("文档处理失败: " + safeFailureReason(e), e);
        } finally {
            documentRepository.save(document);
        }

        // 新文档已稳定为 COMPLETED 后再尝试替代旧版本；替代失败只记录告警，不破坏新文档索引
        if (document.getStatus() == DocumentStatus.COMPLETED
                && document.getSupersedesDocumentId() != null
                && !document.getSupersedesDocumentId().isBlank()) {
            try {
                supersedeOldDocument(document);
            } catch (Exception e) {
                log.warn("文档替代旧版本失败，新文档已稳定为 COMPLETED，待人工修复 documentId={} oldDocumentId={} reason={}",
                        document.getId().value(), document.getSupersedesDocumentId(), safeFailureReason(e));
            }
        }
    }

    private void supersedeOldDocument(Document newDocument) {
        DocumentId oldId = new DocumentId(newDocument.getSupersedesDocumentId());
        Document oldDoc = documentRepository.findById(oldId).orElse(null);
        if (oldDoc == null) {
            log.warn("被替代文档不存在，跳过替代 documentId={}", oldId.value());
            return;
        }

        oldDoc.supersedeBy(newDocument, newDocument.getEffectiveFrom());
        documentRepository.save(oldDoc);
        log.info("文档替代完成（索引将在检索时通过文档真实状态过滤）oldDocumentId={} newDocumentId={} effectiveTo={}",
                oldDoc.getId().value(), newDocument.getId().value(), newDocument.getEffectiveFrom());
    }

    private DocumentMetadata toMetadata(Document document) {
        return new DocumentMetadata(
                document.getSourceType(),
                document.getCanonicalKey(),
                document.getVersionLabel(),
                document.getEffectiveFrom(),
                document.getEffectiveTo(),
                document.getSupersedesDocumentId(),
                document.getAuthorityLevel(),
                document.getJurisdiction()
        );
    }

    private List<DocumentChunk> parseDocumentChunks(Document document, String contentObjectId) {
        InputStream content = documentContentStorePort.open(contentObjectId);
        String text = documentParserPort.parse(content, document.getFileName());
        log.info("文档解析完成 documentId={} chars={}", document.getId().value(), text.length());

        List<DocumentChunk> chunks = recursiveChunkingStrategy.split(text);
        if (chunks.isEmpty()) {
            throw new DomainException("文档未解析出有效文本");
        }
        log.info("文档分块完成 documentId={} chunks={}", document.getId().value(), chunks.size());
        return chunks;
    }

    private void storeDocumentIndexes(Document document, List<DocumentChunk> chunks) {
        List<float[]> embeddings = embeddingPort.embed(
                chunks.stream().map(DocumentChunk::text).toList()
        );
        log.info("文档向量化完成 documentId={} embeddings={}", document.getId().value(), embeddings.size());

        DocumentMetadata metadata = toMetadata(document);
        vectorStorePort.store(
                document.getKnowledgeBaseId(),
                document.getId(),
                document.getFileName(),
                chunks,
                embeddings,
                metadata
        );
        log.info("文档向量写入完成 documentId={}", document.getId().value());

        chunkSearchIndexPort.store(
                document.getKnowledgeBaseId(),
                document.getId(),
                document.getFileName(),
                chunks,
                metadata
        );
        log.info("文档关键词索引写入完成 documentId={}", document.getId().value());
    }

    private void cleanupDocumentIndexesQuietly(Document document) {
        try {
            vectorStorePort.deleteByDocumentId(document.getKnowledgeBaseId(), document.getId());
        } catch (Exception ignored) {
            // 保留主异常；失败文档重新上传时会再次清理残留向量。
        }
        try {
            chunkSearchIndexPort.deleteByDocumentId(document.getKnowledgeBaseId(), document.getId());
        } catch (Exception ignored) {
            // 保留主异常；失败文档重新上传时会再次清理残留索引。
        }
    }

    private void deleteContentObjectQuietly(Document document) {
        if (document.getContentObjectId() == null || document.getContentObjectId().isBlank()) {
            return;
        }
        try {
            documentContentStorePort.delete(document.getContentObjectId());
        } catch (Exception ignored) {
            // 删除文档时不因原始对象清理失败中断主流程；摄入残留可由运维脚本清理。
        }
    }

    @Override
    public RagContext prepare(Query query) {
        KnowledgeBase kb = requireKnowledgeBase(query.knowledgeBaseId());
        accessControlPort.checkKnowledgeBaseAccess(kb, "KB_READ");
        ChatSession chatSession = resolveChatSession(query, kb);
        if (chatMemoryEnabled) {
            appendMessage(chatSession, ChatRole.USER, query.text(), List.of());
            chatSession.renameFromUserQuestion(query.text());
            chatSessionRepository.save(chatSession);
            summarizeIfNeeded(chatSession);
        }
        PreparedQuery preparedQuery = prepareQuery(query.text());
        LocalDate asOfDate = resolveAsOfDate(query);

        List<ChunkReference> results = retrieveReferences(query, preparedQuery, asOfDate);
        StringBuilder contextBuilder = new StringBuilder();
        appendMemoryContext(contextBuilder, chatSession);
        appendSourceContexts(contextBuilder, results, asOfDate);

        String prompt = String.format(promptTemplate,
                contextBuilder.toString(),
                query.text()
        );

        return new RagContext(
                prompt,
                new ArrayList<>(results),
                chatSession == null ? null : chatSession.getId().value(),
                chatSession == null ? null : chatSession.getOwnerUserId()
        );
    }

    private LocalDate resolveAsOfDate(Query query) {
        if (query.asOfDate() != null) {
            return query.asOfDate();
        }
        LocalDate parsed = parseTemporalIntent(query.text());
        return parsed != null ? parsed : LocalDate.now();
    }

    private LocalDate parseTemporalIntent(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String normalized = text.replaceAll("\s+", "");
        java.util.regex.Matcher m;

        m = java.util.regex.Pattern.compile("(\\d{4})年(?:规定|规则|法规|政策|制度|办法|意见|通知|新规|旧规|适用|有效|生效)").matcher(normalized);
        if (m.find()) {
            int year = Integer.parseInt(m.group(1));
            return LocalDate.of(year, 12, 31);
        }

        m = java.util.regex.Pattern.compile("(\\d{2})年(?:新规|新规程|新规定|新办法|新政策|新制度)").matcher(normalized);
        if (m.find()) {
            int year = Integer.parseInt(m.group(1));
            int fullYear = year >= 50 ? 1900 + year : 2000 + year;
            return LocalDate.of(fullYear, 12, 31);
        }

        m = java.util.regex.Pattern.compile("现在适用|当前适用|现在规定|目前有效|现行").matcher(normalized);
        if (m.find()) {
            return LocalDate.now();
        }

        return null;
    }

    /**
     * 执行混合检索：Milvus 向量召回与 Mongo BM25 关键词召回并行执行，
     * 然后合并重排、按 Mongo 权威状态兜底过滤、按 canonicalKey 去重。
     * 所有召回均限定 knowledgeBaseId，不跨库查询。
     */
    private List<ChunkReference> retrieveReferences(Query query, PreparedQuery preparedQuery, LocalDate asOfDate) {
        RetrievalFilter filter = new RetrievalFilter(asOfDate, query.sourceType(), query.jurisdiction());
        CompletableFuture<List<ChunkReference>> vectorFuture = CompletableFuture.supplyAsync(
                () -> vectorStorePort.search(query.knowledgeBaseId(), preparedQuery.embedding(), vectorCandidateK, filter),
                retrievalExecutor
        );
        CompletableFuture<List<ChunkReference>> keywordFuture = CompletableFuture.supplyAsync(
                () -> chunkSearchIndexPort.search(query.knowledgeBaseId(), preparedQuery.effectiveText(), keywordCandidateK, filter),
                retrievalExecutor
        );
        List<ChunkReference> merged = mergeAndRerank(await(vectorFuture), await(keywordFuture));
        List<ChunkReference> filtered = filterByDocumentEffectiveDate(merged, query, asOfDate);
        return deduplicateByCanonicalKey(filtered);
    }

    /**
     * 用 Mongo 权威文档状态对检索结果做最终兜底过滤。
     * 排除索引未同步（非 SYNCED）的文档；校验 effective 日期、sourceType、jurisdiction；
     * 并用 Mongo 真实 metadata 覆盖索引中的旧值，保证引用信息的准确性。
     * 此步骤必须在 topK 截断前执行。
     */
    private List<ChunkReference> filterByDocumentEffectiveDate(List<ChunkReference> references, Query query, LocalDate asOfDate) {
        if (references.isEmpty()) {
            return references;
        }
        java.util.Set<String> documentIds = references.stream()
                .map(ChunkReference::documentId)
                .collect(java.util.stream.Collectors.toSet());
        java.util.Map<String, Document> documentMap = new java.util.HashMap<>();
        for (String docId : documentIds) {
            documentRepository.findById(new DocumentId(docId))
                    .ifPresent(doc -> documentMap.put(docId, doc));
        }
        return references.stream()
                .filter(ref -> {
                    Document doc = documentMap.get(ref.documentId());
                    if (doc == null) {
                        return true;
                    }
                    // 排除索引未同步的文档：其引用元数据来自旧索引，不可信
                    if (doc.getIndexStatus() != DocumentIndexStatus.SYNCED) {
                        return false;
                    }
                    if (!doc.isEffectiveOn(asOfDate)) {
                        return false;
                    }
                    // sourceType 兜底过滤
                    if (query.sourceType() != null && query.sourceType() != doc.getSourceType()) {
                        return false;
                    }
                    // jurisdiction 兜底过滤
                    if (query.jurisdiction() != null && !query.jurisdiction().equals(doc.getJurisdiction())) {
                        return false;
                    }
                    return true;
                })
                .map(ref -> {
                    Document doc = documentMap.get(ref.documentId());
                    if (doc == null) {
                        return ref;
                    }
                    // 用 Mongo 权威 metadata 覆盖索引中的旧 metadata
                    return new ChunkReference(
                            ref.documentId(),
                            ref.documentName(),
                            ref.chunkIndex(),
                            ref.chunkText(),
                            ref.score(),
                            ref.startPosition(),
                            ref.endPosition(),
                            doc.getCanonicalKey(),
                            doc.getVersionLabel(),
                            doc.getEffectiveFrom(),
                            doc.getEffectiveTo(),
                            doc.getLifecycleStatus(),
                            doc.getAuthorityLevel(),
                            doc.getJurisdiction()
                    );
                })
                .toList();
    }

    private void appendSourceContexts(StringBuilder contextBuilder, List<ChunkReference> results, LocalDate asOfDate) {
        for (int i = 0; i < results.size(); i++) {
            ChunkReference result = results.get(i);
            contextBuilder.append("<source id=\"").append(i + 1).append("\">\n")
                    .append("<documentName>").append(escapeSourceMetadata(result.documentName())).append("</documentName>\n")
                    .append("<chunkIndex>").append(result.chunkIndex()).append("</chunkIndex>\n")
                    .append("<score>").append(String.format(java.util.Locale.ROOT, "%.4f", result.score())).append("</score>\n");
            if (result.canonicalKey() != null && !result.canonicalKey().isBlank()) {
                contextBuilder.append("<canonicalKey>").append(escapeSourceMetadata(result.canonicalKey())).append("</canonicalKey>\n");
            }
            if (result.versionLabel() != null && !result.versionLabel().isBlank()) {
                contextBuilder.append("<versionLabel>").append(escapeSourceMetadata(result.versionLabel())).append("</versionLabel>\n");
            }
            if (result.effectiveFrom() != null) {
                contextBuilder.append("<effectiveFrom>").append(result.effectiveFrom()).append("</effectiveFrom>\n");
            }
            if (result.effectiveTo() != null) {
                contextBuilder.append("<effectiveTo>").append(result.effectiveTo()).append("</effectiveTo>\n");
            }
            if (result.lifecycleStatus() != null) {
                contextBuilder.append("<lifecycleStatus>").append(result.lifecycleStatus()).append("</lifecycleStatus>\n");
            }
            if (result.authorityLevel() > 0) {
                contextBuilder.append("<authorityLevel>").append(result.authorityLevel()).append("</authorityLevel>\n");
            }
            contextBuilder.append("<chunkText>\n")
                    .append(result.chunkText())
                    .append("\n</chunkText>\n")
                    .append("</source>\n\n");
        }
    }

    @Override
    public void complete(RagContext ragContext, String answerText) {
        if (!chatMemoryEnabled || ragContext.sessionId() == null || answerText == null || answerText.isBlank()) {
            return;
        }
        ChatSession session = requireSessionForOwner(
                new ChatSessionId(ragContext.sessionId()),
                ragContext.ownerUserId()
        );
        appendMessage(session, ChatRole.ASSISTANT, answerText, ragContext.references());
        chatSessionRepository.save(session);
    }

    @Override
    public ChatSession getOrCreateCurrentSession(KnowledgeBaseId knowledgeBaseId) {
        KnowledgeBase kb = requireKnowledgeBase(knowledgeBaseId);
        accessControlPort.checkKnowledgeBaseAccess(kb, "KB_READ");
        String ownerUserId = accessControlPort.currentUserId();
        return chatSessionRepository.findLatestByOwnerUserIdAndKnowledgeBaseId(ownerUserId, knowledgeBaseId)
                .orElseGet(() -> chatSessionRepository.save(ChatSession.create(ownerUserId, knowledgeBaseId)));
    }

    @Override
    public ChatSession createChatSession(KnowledgeBaseId knowledgeBaseId) {
        KnowledgeBase kb = requireKnowledgeBase(knowledgeBaseId);
        accessControlPort.checkKnowledgeBaseAccess(kb, "KB_READ");
        return chatSessionRepository.save(ChatSession.create(accessControlPort.currentUserId(), knowledgeBaseId));
    }

    @Override
    public List<ChatMessage> listChatMessages(ChatSessionId sessionId, int page, int size) {
        requireSessionForCurrentUser(sessionId);
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 100));
        return chatMessageRepository.findBySessionId(sessionId, safePage, safeSize);
    }

    @Override
    public Document updateMetadata(DocumentId id, PatchDocumentMetadata patch) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new DomainException("未找到文档: " + id.value()));
        KnowledgeBase kb = requireKnowledgeBase(document.getKnowledgeBaseId());
        accessControlPort.checkKnowledgeBaseAccess(kb, "KB_WRITE");

        String before = snapshotMetadata(document);
        document.patchMetadata(patch);
        document.markIndexStale();
        document = documentRepository.save(document);
        createIndexRefreshJob(document);
        auditEvent(document, "METADATA_UPDATED", before, snapshotMetadata(document), null);
        return document;
    }

    @Override
    public void supersede(DocumentId oldDocumentId, DocumentId newDocumentId, LocalDate effectiveTo) {
        Document oldDoc = documentRepository.findById(oldDocumentId)
                .orElseThrow(() -> new DomainException("未找到旧文档: " + oldDocumentId.value()));
        Document newDoc = documentRepository.findById(newDocumentId)
                .orElseThrow(() -> new DomainException("未找到新文档: " + newDocumentId.value()));
        KnowledgeBase kb = requireKnowledgeBase(oldDoc.getKnowledgeBaseId());
        accessControlPort.checkKnowledgeBaseAccess(kb, "KB_WRITE");

        if (!oldDoc.getKnowledgeBaseId().equals(newDoc.getKnowledgeBaseId())) {
            throw new DomainException("被替代文档与替代文档必须属于同一知识库");
        }
        if (oldDoc.getLifecycleStatus() != DocumentLifecycleStatus.ACTIVE) {
            throw new DomainException("仅 ACTIVE 文档可被替代");
        }
        if (newDoc.getStatus() != DocumentStatus.COMPLETED) {
            throw new DomainException("替代文档必须已完成摄入");
        }

        String before = snapshotMetadata(oldDoc);
        oldDoc.supersedeBy(newDoc, effectiveTo);
        oldDoc.markIndexStale();
        oldDoc = documentRepository.save(oldDoc);
        createIndexRefreshJob(oldDoc);
        auditEvent(oldDoc, "SUPERSEDED", before, snapshotMetadata(oldDoc),
                "newDocumentId=" + newDocumentId.value());
    }

    @Override
    public Document retire(DocumentId id, LocalDate effectiveTo) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new DomainException("未找到文档: " + id.value()));
        KnowledgeBase kb = requireKnowledgeBase(document.getKnowledgeBaseId());
        accessControlPort.checkKnowledgeBaseAccess(kb, "KB_WRITE");

        String before = snapshotMetadata(document);
        document.retire(effectiveTo);
        document.markIndexStale();
        document = documentRepository.save(document);
        createIndexRefreshJob(document);
        auditEvent(document, "RETIRED", before, snapshotMetadata(document), null);
        return document;
    }

    @Override
    public Document reactivate(DocumentId id) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new DomainException("未找到文档: " + id.value()));
        KnowledgeBase kb = requireKnowledgeBase(document.getKnowledgeBaseId());
        accessControlPort.checkKnowledgeBaseAccess(kb, "KB_WRITE");

        String before = snapshotMetadata(document);
        document.reactivate();
        document.markIndexStale();
        document = documentRepository.save(document);
        createIndexRefreshJob(document);
        auditEvent(document, "REACTIVATED", before, snapshotMetadata(document), null);
        return document;
    }

    @Override
    public Document reindex(DocumentId id) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new DomainException("未找到文档: " + id.value()));
        KnowledgeBase kb = requireKnowledgeBase(document.getKnowledgeBaseId());
        accessControlPort.checkKnowledgeBaseAccess(kb, "KB_WRITE");

        document.markIndexStale();
        document = documentRepository.save(document);
        createIndexRefreshJob(document);
        auditEvent(document, "INDEX_REFRESH_REQUESTED", snapshotMetadata(document), snapshotMetadata(document), null);
        return document;
    }

    @Override
    public List<Document> getVersionChain(DocumentId id) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new DomainException("未找到文档: " + id.value()));
        KnowledgeBase kb = requireKnowledgeBase(document.getKnowledgeBaseId());
        accessControlPort.checkKnowledgeBaseAccess(kb, "KB_READ");

        java.util.Set<String> seen = new java.util.HashSet<>();
        List<Document> chain = new ArrayList<>();

        // 向后追溯：当前文档替代了谁
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
                break; // 防循环
            }
        }

        // 加入当前文档
        if (seen.add(document.getId().value())) {
            chain.add(document);
        }

        // 向前追踪：谁替代了当前文档（递归）
        current = document;
        while (true) {
            List<Document> nextDocs = documentRepository.findBySupersedesDocumentId(current.getId())
                    .stream()
                    .filter(d -> d.getKnowledgeBaseId().equals(document.getKnowledgeBaseId()))
                    .toList();
            if (nextDocs.isEmpty()) {
                break;
            }
            // 通常只有一个后继，取第一个
            Document next = nextDocs.getFirst();
            if (!seen.add(next.getId().value())) {
                break; // 防循环
            }
            chain.add(next);
            current = next;
        }

        // 辅助分组：canonicalKey 非空时，把同 KB 下同 canonicalKey 的文档也纳入
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
        KnowledgeBase kb = requireKnowledgeBase(document.getKnowledgeBaseId());
        accessControlPort.checkKnowledgeBaseAccess(kb, "KB_READ");

        return auditEventStorePort.findByDocumentId(id).stream()
                .map(e -> new GetDocumentAuditEventsUseCase.DocumentAuditEvent(
                        e.id(),
                        e.documentId().value(),
                        e.knowledgeBaseId().value(),
                        e.actorUserId(),
                        e.action(),
                        e.beforeSnapshot(),
                        e.afterSnapshot(),
                        e.reason(),
                        e.createdAt()
                ))
                .toList();
    }

    /** 为文档创建索引刷新任务，确保 metadata 变更异步同步到检索索引。 */
    private void createIndexRefreshJob(Document document) {
        var job = com.synapse.kb.model.DocumentIndexRefreshJob.create(
                document.getId(),
                document.getKnowledgeBaseId(),
                document.getMetadataVersion()
        );
        refreshJobRepository.save(job);
    }

    /** 将文档时效 metadata 序列化为审计日志的快照字符串。 */
    private String snapshotMetadata(Document document) {
        return String.format(
                "sourceType=%s,canonicalKey=%s,versionLabel=%s,effectiveFrom=%s,effectiveTo=%s," +
                "lifecycleStatus=%s,authorityLevel=%d,jurisdiction=%s",
                document.getSourceType(),
                document.getCanonicalKey(),
                document.getVersionLabel(),
                document.getEffectiveFrom(),
                document.getEffectiveTo(),
                document.getLifecycleStatus(),
                document.getAuthorityLevel(),
                document.getJurisdiction()
        );
    }

    private void auditEvent(Document document, String action, String before, String after, String reason) {
        try {
            auditEventStorePort.save(new AuditEventStorePort.AuditEvent(
                    java.util.UUID.randomUUID().toString(),
                    document.getId(),
                    document.getKnowledgeBaseId(),
                    accessControlPort.currentUserId(),
                    action,
                    before,
                    after,
                    reason,
                    Instant.now()
            ));
        } catch (Exception e) {
            log.warn("审计日志写入失败 documentId={} action={}", document.getId().value(), action, e);
        }
    }

    private ChatSession resolveChatSession(Query query, KnowledgeBase kb) {
        if (!chatMemoryEnabled) {
            return null;
        }
        if (query.sessionId() != null) {
            ChatSession session = requireSessionForCurrentUser(new ChatSessionId(query.sessionId()));
            if (!session.getKnowledgeBaseId().equals(query.knowledgeBaseId())) {
                throw new DomainException("聊天会话不属于当前知识库");
            }
            return session;
        }
        return chatSessionRepository
                .findLatestByOwnerUserIdAndKnowledgeBaseId(accessControlPort.currentUserId(), kb.getId())
                .orElseGet(() -> chatSessionRepository.save(ChatSession.create(accessControlPort.currentUserId(), kb.getId())));
    }

    private ChatSession requireSessionForCurrentUser(ChatSessionId sessionId) {
        return requireSessionForOwner(sessionId, accessControlPort.currentUserId());
    }

    private ChatSession requireSessionForOwner(ChatSessionId sessionId, String ownerUserId) {
        ChatSession session = chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> new DomainException("未找到聊天会话: " + sessionId.value()));
        if (ownerUserId == null || !ownerUserId.equals(session.getOwnerUserId())) {
            throw new DomainException("无权访问该聊天会话");
        }
        return session;
    }

    private void appendMessage(ChatSession session, ChatRole role, String content, List<ChunkReference> references) {
        long sequence = chatSessionRepository.nextMessageSequence(session.getId());
        session.recordMessageSequence(sequence);
        ChatMessage message = ChatMessage.createWithSequence(session, role, content, references, sequence);
        chatMessageRepository.save(message);
    }

    private void summarizeIfNeeded(ChatSession session) {
        long memoryMaxSequence = Math.max(0, session.getMessageCount() - 1);
        long unsummarizedHistoryCount = memoryMaxSequence - session.getSummarizedUntilSequence();
        if (unsummarizedHistoryCount <= summaryTriggerMessageCount) {
            return;
        }

        long summarizeUntil = Math.max(session.getSummarizedUntilSequence(), memoryMaxSequence - recentMessageLimit);
        if (summarizeUntil <= session.getSummarizedUntilSequence()) {
            return;
        }
        List<ChatMessage> messages = chatMessageRepository.findBySessionIdAndSequenceBetween(
                session.getId(),
                session.getSummarizedUntilSequence(),
                summarizeUntil
        );
        if (messages.isEmpty()) {
            return;
        }

        try {
            String summary = chatMemorySummarizerPort.summarize(session.getSummary(), messages, maxSummaryChars);
            session.updateSummary(summary, summarizeUntil);
            chatSessionRepository.save(session);
            log.debug("聊天记忆摘要完成 sessionId={} summarizedUntil={}",
                    session.getId().value(), summarizeUntil);
        } catch (Exception e) {
            log.warn("聊天记忆摘要失败，回退最近消息 sessionId={} reason={}",
                    session.getId().value(), e.getMessage());
        }
    }

    private void appendMemoryContext(StringBuilder contextBuilder, ChatSession session) {
        if (!chatMemoryEnabled || session == null) {
            return;
        }
        if (session.getSummary() != null && !session.getSummary().isBlank()) {
            contextBuilder.append("<conversation_summary>\n")
                    .append(session.getSummary())
                    .append("\n</conversation_summary>\n\n");
        }

        long memoryMaxSequence = Math.max(0, session.getMessageCount() - 1);
        List<ChatMessage> recentMessages = recentMessageLimit == 0
                ? List.of()
                : chatMessageRepository.findRecentBySessionIdBeforeOrEqual(
                        session.getId(), memoryMaxSequence, recentMessageLimit);
        if (recentMessages.isEmpty()) {
            return;
        }

        contextBuilder.append("<recent_dialogue>\n");
        for (ChatMessage message : recentMessages) {
            contextBuilder.append(message.role() == ChatRole.USER ? "用户: " : "助手: ")
                    .append(message.content())
                    .append('\n');
        }
        contextBuilder.append("</recent_dialogue>\n\n");
    }

    private PreparedQuery prepareQuery(String originalText) {
        float[] originalEmbedding = embeddingPort.embed(originalText);
        if (!queryRewriteEnabled) {
            return new PreparedQuery(originalText, originalEmbedding);
        }

        try {
            String rewritten = queryRewritePort.rewrite(originalText);
            if (rewritten == null || rewritten.isBlank() || rewritten.equals(originalText)) {
                return new PreparedQuery(originalText, originalEmbedding);
            }

            float[] rewrittenEmbedding = embeddingPort.embed(rewritten);
            double similarity = cosineSimilarity(originalEmbedding, rewrittenEmbedding);
            if (similarity < queryRewriteSimilarityThreshold) {
                log.info("Query 改写未通过相似度门禁 similarity={} threshold={}",
                        String.format("%.4f", similarity), queryRewriteSimilarityThreshold);
                return new PreparedQuery(originalText, originalEmbedding);
            }

            log.debug("Query 改写生效 similarity={}", String.format("%.4f", similarity));
            return new PreparedQuery(rewritten, rewrittenEmbedding);
        } catch (Exception e) {
            log.warn("Query 改写失败，回退原始查询: {}", e.getMessage());
            return new PreparedQuery(originalText, originalEmbedding);
        }
    }

    /**
     * 融合向量与关键词召回结果，按配置的权重加权打分后降序排列。
     * 同一片段同时被两种方式召回时，取加权最高分。
     */
    private List<ChunkReference> mergeAndRerank(List<ChunkReference> vectorResults, List<ChunkReference> keywordResults) {
        Map<String, RankedReference> merged = new LinkedHashMap<>();
        for (ChunkReference reference : vectorResults) {
            merged.computeIfAbsent(referenceKey(reference), key -> new RankedReference(reference))
                    .vectorScore = reference.score();
        }
        for (ChunkReference reference : keywordResults) {
            merged.computeIfAbsent(referenceKey(reference), key -> new RankedReference(reference))
                    .keywordScore = reference.score();
        }

        return merged.values().stream()
                .map(this::toFinalReference)
                .sorted(Comparator.comparing(ChunkReference::score).reversed())
                .toList();
    }

    /**
     * 按 canonicalKey 去重：同 key 只保留权威等级最高、生效日期最新的片段。
     * 无 canonicalKey 的片段不参与去重，直接保留。最后在 topK 范围内截断。
     */
    private List<ChunkReference> deduplicateByCanonicalKey(List<ChunkReference> ranked) {
        Map<String, ChunkReference> bestByCanonicalKey = new LinkedHashMap<>();
        List<ChunkReference> result = new ArrayList<>();

        for (ChunkReference ref : ranked) {
            String key = ref.canonicalKey();
            if (key == null || key.isBlank()) {
                result.add(ref);
                continue;
            }
            ChunkReference existing = bestByCanonicalKey.get(key);
            if (existing == null) {
                bestByCanonicalKey.put(key, ref);
                result.add(ref);
            } else {
                if (ref.authorityLevel() > existing.authorityLevel()
                        || (ref.authorityLevel() == existing.authorityLevel()
                        && ref.effectiveFrom() != null
                        && existing.effectiveFrom() != null
                        && ref.effectiveFrom().isAfter(existing.effectiveFrom()))) {
                    bestByCanonicalKey.put(key, ref);
                    int idx = result.indexOf(existing);
                    if (idx >= 0) {
                        result.set(idx, ref);
                    }
                }
            }
        }
        return result.stream().limit(topK).toList();
    }

    private ChunkReference toFinalReference(RankedReference ranked) {
        ChunkReference ref = ranked.reference;
        float score = (float) Math.min(1.0, vectorWeight * ranked.vectorScore + keywordWeight * ranked.keywordScore);
        return new ChunkReference(
                ref.documentId(),
                ref.documentName(),
                ref.chunkIndex(),
                ref.chunkText(),
                score,
                ref.startPosition(),
                ref.endPosition(),
                ref.canonicalKey(),
                ref.versionLabel(),
                ref.effectiveFrom(),
                ref.effectiveTo(),
                ref.lifecycleStatus(),
                ref.authorityLevel(),
                ref.jurisdiction()
        );
    }

    private String referenceKey(ChunkReference reference) {
        return reference.documentId() + ":" + reference.chunkIndex();
    }

    private String escapeSourceMetadata(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private List<ChunkReference> await(CompletableFuture<List<ChunkReference>> future) {
        try {
            return future.join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new DomainException("检索失败", e);
        }
    }

    private double cosineSimilarity(float[] left, float[] right) {
        if (left.length != right.length) {
            throw new DomainException("向量维度不一致");
        }
        double dot = 0;
        double leftNorm = 0;
        double rightNorm = 0;
        for (int i = 0; i < left.length; i++) {
            dot += left[i] * right[i];
            leftNorm += left[i] * left[i];
            rightNorm += right[i] * right[i];
        }
        if (leftNorm == 0 || rightNorm == 0) {
            return 0;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    private KnowledgeBase requireKnowledgeBase(KnowledgeBaseId id) {
        return knowledgeBaseRepository.findById(id)
                .orElseThrow(() -> new DomainException("未找到知识库: " + id.value()));
    }

    private List<KnowledgeBase> filterVisible(List<KnowledgeBase> knowledgeBases) {
        if (accessControlPort.isAdmin()) {
            return knowledgeBases;
        }
        String currentUserId = accessControlPort.currentUserId();
        return knowledgeBases.stream()
                .filter(kb -> currentUserId.equals(kb.getOwnerUserId()))
                .toList();
    }

    private String safeFailureReason(Exception e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) {
            message = e.getClass().getSimpleName();
        }
        return message.length() <= MAX_FAILURE_REASON_LENGTH
                ? message
                : message.substring(0, MAX_FAILURE_REASON_LENGTH);
    }

    private record PreparedQuery(String effectiveText, float[] embedding) {
    }

    private static class RankedReference {
        private final ChunkReference reference;
        private float vectorScore;
        private float keywordScore;

        private RankedReference(ChunkReference reference) {
            this.reference = reference;
        }
    }
}
