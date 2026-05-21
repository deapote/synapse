package com.synapse.kb.port.service;

import com.synapse.kb.model.*;
import com.synapse.kb.port.in.ProcessIngestionJobUseCase;
import com.synapse.kb.port.out.DocumentContentStorePort;
import com.synapse.kb.port.out.DocumentParserPort;
import com.synapse.kb.port.out.IngestionJobRepository;
import com.synapse.kb.port.service.support.DocumentIndexingService;
import com.synapse.kb.port.service.support.FailureReasonSanitizer;
import com.synapse.kb.repository.DocumentRepository;
import com.synapse.kb.service.RecursiveChunkingStrategy;
import com.synapse.shared.exception.DomainException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * 文档摄入服务，编排后台文档解析与索引流程。
 * 负责解析原始内容、文本切分、向量/关键词索引写入及旧版本替代。
 */
public class DocumentIngestionService implements ProcessIngestionJobUseCase {

    private static final Logger log = LoggerFactory.getLogger(DocumentIngestionService.class);

    private final DocumentRepository documentRepository;
    private final DocumentParserPort documentParserPort;
    private final RecursiveChunkingStrategy recursiveChunkingStrategy;
    private final DocumentContentStorePort documentContentStorePort;
    private final DocumentIndexingService documentIndexingService;
    private final IngestionJobRepository ingestionJobRepository;
    private final FailureReasonSanitizer failureReasonSanitizer;
    private final int ingestionMaxAttempts;
    private final Duration ingestionLeaseDuration;

    public DocumentIngestionService(DocumentRepository documentRepository,
                                    DocumentParserPort documentParserPort,
                                    RecursiveChunkingStrategy recursiveChunkingStrategy,
                                    DocumentContentStorePort documentContentStorePort,
                                    DocumentIndexingService documentIndexingService,
                                    IngestionJobRepository ingestionJobRepository,
                                    FailureReasonSanitizer failureReasonSanitizer,
                                    int ingestionMaxAttempts,
                                    Duration ingestionLeaseDuration) {
        this.documentRepository = documentRepository;
        this.documentParserPort = documentParserPort;
        this.recursiveChunkingStrategy = recursiveChunkingStrategy;
        this.documentContentStorePort = documentContentStorePort;
        this.documentIndexingService = documentIndexingService;
        this.ingestionJobRepository = ingestionJobRepository;
        this.failureReasonSanitizer = failureReasonSanitizer;
        this.ingestionMaxAttempts = Math.max(1, ingestionMaxAttempts);
        this.ingestionLeaseDuration = ingestionLeaseDuration == null ? Duration.ofMinutes(5) : ingestionLeaseDuration;
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

    private void handleIngestionFailure(IngestionJob job, Document document, Exception e) {
        String reason = failureReasonSanitizer.safeFailureReason(e);
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
            documentIndexingService.storeDocumentIndexes(document, chunks);
            documentIndexingService.saveDocumentChunks(document.getId(), chunks);

            document.setChunkCount(chunks.size());
            document.transitionTo(DocumentStatus.COMPLETED);

        } catch (Exception e) {
            documentIndexingService.cleanupDocumentIndexesQuietly(document);
            throw new DomainException("文档处理失败: " + failureReasonSanitizer.safeFailureReason(e), e);
        } finally {
            documentRepository.save(document);
        }

        if (document.getStatus() == DocumentStatus.COMPLETED
                && document.getSupersedesDocumentId() != null
                && !document.getSupersedesDocumentId().isBlank()) {
            try {
                supersedeOldDocument(document);
            } catch (Exception e) {
                log.warn("文档替代旧版本失败，新文档已稳定为 COMPLETED，待人工修复 documentId={} oldDocumentId={} reason={}",
                        document.getId().value(), document.getSupersedesDocumentId(), failureReasonSanitizer.safeFailureReason(e));
            }
        }
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
}
