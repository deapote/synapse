package com.synapse.kb.port.out;

import com.synapse.kb.model.ChunkReference;
import com.synapse.kb.model.DocumentChunk;
import com.synapse.kb.model.DocumentId;
import com.synapse.kb.model.DocumentMetadata;
import com.synapse.kb.model.KnowledgeBaseId;

import java.util.List;

/** 向量存储出站端口。所有操作必须限定 knowledgeBaseId。 */
public interface VectorStorePort {

    void store(KnowledgeBaseId knowledgeBaseId, DocumentId documentId, String documentName,
               List<DocumentChunk> chunks, List<float[]> embeddings, DocumentMetadata metadata);

    List<ChunkReference> search(KnowledgeBaseId knowledgeBaseId, float[] queryEmbedding, int topK, RetrievalFilter filter);

    void deleteByDocumentId(KnowledgeBaseId knowledgeBaseId, DocumentId documentId);

    /**
     * 更新已存储文档的 scalar 元数据。
     * 若底层向量库不支持按条件更新 scalar 字段，实现方可返回失败或抛异常。
     */
    void updateDocumentMetadata(KnowledgeBaseId knowledgeBaseId, DocumentId documentId, DocumentMetadata metadata);
}
