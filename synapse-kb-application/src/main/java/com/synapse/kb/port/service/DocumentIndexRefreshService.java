package com.synapse.kb.port.service;

import com.synapse.kb.model.*;
import com.synapse.kb.port.in.ProcessDocumentIndexRefreshJobUseCase;
import com.synapse.kb.port.out.*;
import com.synapse.kb.port.service.support.FailureReasonSanitizer;
import com.synapse.kb.port.service.support.MetadataSnapshotter;
import com.synapse.kb.repository.DocumentChunkRepository;
import com.synapse.kb.repository.DocumentRepository;
import com.synapse.shared.exception.DomainException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * 文档索引刷新服务，编排后台索引重建流程。
 * 重新生成文档分块的向量与关键词索引，并同步更新 Milvus 与 Mongo。
 */
public class DocumentIndexRefreshService implements ProcessDocumentIndexRefreshJobUseCase {

    private static final Logger log = LoggerFactory.getLogger(DocumentIndexRefreshService.class);

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final EmbeddingPort embeddingPort;
    private final VectorStorePort vectorStorePort;
    private final ChunkSearchIndexPort chunkSearchIndexPort;
    private final DocumentIndexRefreshJobRepository refreshJobRepository;
    private final FailureReasonSanitizer failureReasonSanitizer;
    private final MetadataSnapshotter metadataSnapshotter;

    public DocumentIndexRefreshService(DocumentRepository documentRepository,
                                       DocumentChunkRepository documentChunkRepository,
                                       EmbeddingPort embeddingPort,
                                       VectorStorePort vectorStorePort,
                                       ChunkSearchIndexPort chunkSearchIndexPort,
                                       DocumentIndexRefreshJobRepository refreshJobRepository,
                                       FailureReasonSanitizer failureReasonSanitizer,
                                       MetadataSnapshotter metadataSnapshotter) {
        this.documentRepository = documentRepository;
        this.documentChunkRepository = documentChunkRepository;
        this.embeddingPort = embeddingPort;
        this.vectorStorePort = vectorStorePort;
        this.chunkSearchIndexPort = chunkSearchIndexPort;
        this.refreshJobRepository = refreshJobRepository;
        this.failureReasonSanitizer = failureReasonSanitizer;
        this.metadataSnapshotter = metadataSnapshotter;
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

    private void runIndexRefreshTask(DocumentIndexRefreshJob job) {
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

            DocumentMetadata metadata = metadataSnapshotter.toMetadata(document);

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
            String reason = failureReasonSanitizer.safeFailureReason(e);
            document.markIndexFailed(reason);
            documentRepository.save(document);
            job.markFailed(reason, Instant.now().plus(Duration.ofMinutes(1)));
            refreshJobRepository.save(job);
            log.warn("索引刷新失败 jobId={} documentId={} reason={}",
                    job.getId().value(), document.getId().value(), reason, e);
        }
    }
}
