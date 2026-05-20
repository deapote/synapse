package com.synapse.kb.port.out;

import com.synapse.kb.model.ChunkReference;
import com.synapse.kb.model.DocumentChunk;
import com.synapse.kb.model.DocumentId;
import com.synapse.kb.model.DocumentMetadata;
import com.synapse.kb.model.KnowledgeBaseId;

import java.util.List;

/** 文档分块关键词检索索引端口。 */
public interface ChunkSearchIndexPort {

    void store(KnowledgeBaseId knowledgeBaseId, DocumentId documentId, String documentName,
               List<DocumentChunk> chunks, DocumentMetadata metadata);

    /**
     * 安全刷新已有文档的索引。与 {@link #store} 的区别：不在写入新数据前删除旧数据。
     * 如果任何步骤失败，旧索引应尽可能保留，以保证检索不因刷新失败而降级。
     */
    void refreshStore(KnowledgeBaseId knowledgeBaseId, DocumentId documentId, String documentName,
                      List<DocumentChunk> chunks, DocumentMetadata metadata);

    List<ChunkReference> search(KnowledgeBaseId knowledgeBaseId, String query, int topK, RetrievalFilter filter);

    void deleteByDocumentId(KnowledgeBaseId knowledgeBaseId, DocumentId documentId);

    /** 更新已存储文档的元数据。 */
    void updateDocumentMetadata(KnowledgeBaseId knowledgeBaseId, DocumentId documentId, DocumentMetadata metadata);
}
