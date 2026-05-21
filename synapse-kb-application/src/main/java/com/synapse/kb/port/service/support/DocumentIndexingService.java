package com.synapse.kb.port.service.support;

import com.synapse.kb.model.Document;
import com.synapse.kb.model.DocumentChunk;
import com.synapse.kb.model.DocumentId;
import com.synapse.kb.model.DocumentMetadata;
import com.synapse.kb.port.out.ChunkSearchIndexPort;
import com.synapse.kb.port.out.DocumentContentStorePort;
import com.synapse.kb.port.out.EmbeddingPort;
import com.synapse.kb.port.out.VectorStorePort;
import com.synapse.kb.repository.DocumentChunkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 文档索引辅助服务。
 * 统一封装向量写入、关键词索引写入、分块持久化及清理操作。
 */
public class DocumentIndexingService {
    private static final Logger log = LoggerFactory.getLogger(DocumentIndexingService.class);
    private final EmbeddingPort embeddingPort;
    private final VectorStorePort vectorStorePort;
    private final ChunkSearchIndexPort chunkSearchIndexPort;
    private final DocumentChunkRepository documentChunkRepository;
    private final DocumentContentStorePort documentContentStorePort;
    private final MetadataSnapshotter metadataSnapshotter;

    public DocumentIndexingService(EmbeddingPort embeddingPort,
                                   VectorStorePort vectorStorePort,
                                   ChunkSearchIndexPort chunkSearchIndexPort,
                                   DocumentChunkRepository documentChunkRepository,
                                   DocumentContentStorePort documentContentStorePort,
                                   MetadataSnapshotter metadataSnapshotter) {
        this.embeddingPort = embeddingPort;
        this.vectorStorePort = vectorStorePort;
        this.chunkSearchIndexPort = chunkSearchIndexPort;
        this.documentChunkRepository = documentChunkRepository;
        this.documentContentStorePort = documentContentStorePort;
        this.metadataSnapshotter = metadataSnapshotter;
    }

    public void storeDocumentIndexes(Document document, List<DocumentChunk> chunks) {
        List<float[]> embeddings = embeddingPort.embed(
                chunks.stream().map(DocumentChunk::text).toList()
        );
        log.info("文档向量化完成 documentId={} embeddings={}", document.getId().value(), embeddings.size());

        DocumentMetadata metadata = metadataSnapshotter.toMetadata(document);
        vectorStorePort.store(
                document.getKnowledgeBaseId(), document.getId(), document.getFileName(),
                chunks, embeddings, metadata
        );
        log.info("文档向量写入完成 documentId={}", document.getId().value());

        chunkSearchIndexPort.store(
                document.getKnowledgeBaseId(), document.getId(), document.getFileName(),
                chunks, metadata
        );
        log.info("文档关键词索引写入完成 documentId={}", document.getId().value());
    }

    public void saveDocumentChunks(DocumentId documentId, List<DocumentChunk> chunks) {
        documentChunkRepository.save(documentId, chunks);
    }

    public void cleanupDocumentIndexesQuietly(Document document) {
        try {
            vectorStorePort.deleteByDocumentId(document.getKnowledgeBaseId(), document.getId());
        } catch (Exception ignored) {
        }
        try {
            chunkSearchIndexPort.deleteByDocumentId(document.getKnowledgeBaseId(), document.getId());
        } catch (Exception ignored) {
        }
        try {
            documentChunkRepository.deleteByDocumentId(document.getId());
        } catch (Exception ignored) {
        }
    }

    public void deleteContentObjectQuietly(Document document) {
        if (document.getContentObjectId() == null || document.getContentObjectId().isBlank()) {
            return;
        }
        try {
            documentContentStorePort.delete(document.getContentObjectId());
        } catch (Exception ignored) {
        }
    }
}
