package com.synapse.kb.adapter.out.persistence.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "document_chunk_postings")
@CompoundIndexes({
        @CompoundIndex(name = "idx_posting_kb_token", def = "{'knowledgeBaseId': 1, 'token': 1}"),
        @CompoundIndex(name = "idx_posting_kb_doc", def = "{'knowledgeBaseId': 1, 'documentId': 1}")
})
public class ChunkPostingDocument {
    @Id
    private String id;
    private String knowledgeBaseId;
    private String token;
    private String documentId;
    private String documentName;
    private int chunkIndex;
    private String chunkText;
    private int startPosition;
    private int endPosition;
    private int tf;
    private int tokenCount;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getKnowledgeBaseId() { return knowledgeBaseId; }
    public void setKnowledgeBaseId(String knowledgeBaseId) { this.knowledgeBaseId = knowledgeBaseId; }
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public String getDocumentId() { return documentId; }
    public void setDocumentId(String documentId) { this.documentId = documentId; }
    public String getDocumentName() { return documentName; }
    public void setDocumentName(String documentName) { this.documentName = documentName; }
    public int getChunkIndex() { return chunkIndex; }
    public void setChunkIndex(int chunkIndex) { this.chunkIndex = chunkIndex; }
    public String getChunkText() { return chunkText; }
    public void setChunkText(String chunkText) { this.chunkText = chunkText; }
    public int getStartPosition() { return startPosition; }
    public void setStartPosition(int startPosition) { this.startPosition = startPosition; }
    public int getEndPosition() { return endPosition; }
    public void setEndPosition(int endPosition) { this.endPosition = endPosition; }
    public int getTf() { return tf; }
    public void setTf(int tf) { this.tf = tf; }
    public int getTokenCount() { return tokenCount; }
    public void setTokenCount(int tokenCount) { this.tokenCount = tokenCount; }
}
