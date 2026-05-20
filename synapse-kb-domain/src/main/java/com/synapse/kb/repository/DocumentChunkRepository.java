package com.synapse.kb.repository;

import com.synapse.kb.model.DocumentChunk;
import com.synapse.kb.model.DocumentId;

import java.util.List;

/**
 * 文档分块持久化端口。
 */
public interface DocumentChunkRepository {

    void save(DocumentId documentId, List<DocumentChunk> chunks);

    List<DocumentChunk> findByDocumentId(DocumentId documentId);

    void deleteByDocumentId(DocumentId documentId);
}
