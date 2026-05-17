package com.synapse.kb.adapter.out.persistence.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

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
