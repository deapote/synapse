package com.synapse.kb.adapter.out.persistence.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * BM25 语料统计文档，用于计算 IDF。
 * 按 knowledgeBaseId 聚合记录该知识库下的总 chunk 数与总 token 数。
 */
@Document(collection = "document_chunk_corpus_stats")
public class ChunkCorpusStatsDocument {
    @Id
    private String knowledgeBaseId;
    private long totalChunks;
    private long totalTokenCount;

    public String getKnowledgeBaseId() { return knowledgeBaseId; }
    public void setKnowledgeBaseId(String knowledgeBaseId) { this.knowledgeBaseId = knowledgeBaseId; }
    public long getTotalChunks() { return totalChunks; }
    public void setTotalChunks(long totalChunks) { this.totalChunks = totalChunks; }
    public long getTotalTokenCount() { return totalTokenCount; }
    public void setTotalTokenCount(long totalTokenCount) { this.totalTokenCount = totalTokenCount; }
}
