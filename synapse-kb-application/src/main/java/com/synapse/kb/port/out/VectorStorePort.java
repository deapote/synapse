package com.synapse.kb.port.out;

import com.synapse.kb.model.ChunkReference;
import com.synapse.kb.model.DocumentChunk;
import com.synapse.kb.model.DocumentId;
import com.synapse.kb.model.KnowledgeBaseId;

import java.util.List;

/** 向量存储出站端口。所有操作必须限定 knowledgeBaseId。 */
public interface VectorStorePort {

    void store(KnowledgeBaseId knowledgeBaseId, DocumentId documentId, String documentName,
               List<DocumentChunk> chunks, List<float[]> embeddings);

    List<ChunkReference> search(KnowledgeBaseId knowledgeBaseId, float[] queryEmbedding, int topK);

    void deleteByDocumentId(KnowledgeBaseId knowledgeBaseId, DocumentId documentId);
}
