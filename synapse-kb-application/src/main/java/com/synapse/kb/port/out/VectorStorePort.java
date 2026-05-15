package com.synapse.kb.port.out;

import com.synapse.kb.model.ChunkReference;
import com.synapse.kb.model.DocumentChunk;
import com.synapse.kb.model.DocumentId;
import com.synapse.kb.model.KnowledgeBaseId;

import java.util.List;

/**
 * 向量存储端口（出站端口 / SPI）。
 *
 * <p>负责向量数据的存储、相似度检索和删除。
 * 由适配器层（如 {@code MilvusVectorStoreAdapter}）实现。
 */
public interface VectorStorePort {

    /**
     * 存储文档块的向量。
     *
     * @param knowledgeBaseId 知识库 ID，用于隔离不同知识库的数据
     * @param documentId      文档 ID，删除时用于定位该文档的所有向量
     * @param documentName    文档文件名，搜索结果中展示来源用
     * @param chunks          文档分块列表
     * @param embeddings      各块对应的向量列表，顺序与 chunks 一致
     */
    void store(KnowledgeBaseId knowledgeBaseId, DocumentId documentId, String documentName,
               List<DocumentChunk> chunks, List<float[]> embeddings);

    /**
     * 向量相似度检索。
     *
     * @param knowledgeBaseId 知识库 ID，限定搜索范围
     * @param queryEmbedding  查询文本的向量表示
     * @param topK            返回最相似的 K 个结果
     * @return 检索结果列表，按相似度降序排列
     */
    List<ChunkReference> search(KnowledgeBaseId knowledgeBaseId, float[] queryEmbedding, int topK);

    /**
     * 删除指定文档的所有向量。
     *
     * @param knowledgeBaseId 知识库 ID
     * @param documentId      文档 ID
     */
    void deleteByDocumentId(KnowledgeBaseId knowledgeBaseId, DocumentId documentId);
}
