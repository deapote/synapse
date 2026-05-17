package com.synapse.kb.port.out;

import com.synapse.kb.model.ChunkReference;
import com.synapse.kb.model.DocumentChunk;
import com.synapse.kb.model.DocumentId;
import com.synapse.kb.model.KnowledgeBaseId;

import java.util.List;

/** 文档分块关键词检索索引端口。 */
public interface ChunkSearchIndexPort {

    void store(KnowledgeBaseId knowledgeBaseId, DocumentId documentId, String documentName, List<DocumentChunk> chunks);

    List<ChunkReference> search(KnowledgeBaseId knowledgeBaseId, String query, int topK);

    void deleteByDocumentId(KnowledgeBaseId knowledgeBaseId, DocumentId documentId);
}
