package com.synapse.kb.adapter.out.persistence.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;
import java.util.Map;

/**
 * 文档 chunk 关键词索引 MongoDB 文档实体，对应 Chunk 领域模型。
 * 保存切分后的文本、分词结果、词频及文档时效元数据，支撑 BM25 关键词召回。
 */
@Document(collection = "document_chunk_index")
@CompoundIndex(name = "idx_chunk_kb_tokens", def = "{'knowledgeBaseId': 1, 'tokens': 1}")
public class ChunkIndexDocument {
    @Id
    private String id;

    @Indexed
    private String knowledgeBaseId;

    @Indexed
    private String documentId;

    private String documentName;

    private int chunkIndex;

    private String chunkText;

    private int startPosition;

    private int endPosition;

    private List<String> tokens;

    private Map<String, Integer> termFrequencies;

    private int tokenCount;

    private long effectiveFromEpochDay;

    private long effectiveToEpochDay;

    private String lifecycleStatus;

    private String canonicalKey;

    private String versionLabel;

    private int authorityLevel;

    private String jurisdiction;

    private String sourceType;

    public ChunkIndexDocument() {}

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getKnowledgeBaseId() {
        return knowledgeBaseId;
    }

    public void setKnowledgeBaseId(String knowledgeBaseId) {
        this.knowledgeBaseId = knowledgeBaseId;
    }

    public String getDocumentId() {
        return documentId;
    }

    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }

    public String getDocumentName() {
        return documentName;
    }

    public void setDocumentName(String documentName) {
        this.documentName = documentName;
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public void setChunkIndex(int chunkIndex) {
        this.chunkIndex = chunkIndex;
    }

    public String getChunkText() {
        return chunkText;
    }

    public void setChunkText(String chunkText) {
        this.chunkText = chunkText;
    }

    public int getStartPosition() {
        return startPosition;
    }

    public void setStartPosition(int startPosition) {
        this.startPosition = startPosition;
    }

    public int getEndPosition() {
        return endPosition;
    }

    public void setEndPosition(int endPosition) {
        this.endPosition = endPosition;
    }

    public List<String> getTokens() {
        return tokens;
    }

    public void setTokens(List<String> tokens) {
        this.tokens = tokens;
    }

    public Map<String, Integer> getTermFrequencies() {
        return termFrequencies;
    }

    public void setTermFrequencies(Map<String, Integer> termFrequencies) {
        this.termFrequencies = termFrequencies;
    }

    public int getTokenCount() {
        return tokenCount;
    }

    public void setTokenCount(int tokenCount) {
        this.tokenCount = tokenCount;
    }

    public long getEffectiveFromEpochDay() {
        return effectiveFromEpochDay;
    }

    public void setEffectiveFromEpochDay(long effectiveFromEpochDay) {
        this.effectiveFromEpochDay = effectiveFromEpochDay;
    }

    public long getEffectiveToEpochDay() {
        return effectiveToEpochDay;
    }

    public void setEffectiveToEpochDay(long effectiveToEpochDay) {
        this.effectiveToEpochDay = effectiveToEpochDay;
    }

    public String getLifecycleStatus() {
        return lifecycleStatus;
    }

    public void setLifecycleStatus(String lifecycleStatus) {
        this.lifecycleStatus = lifecycleStatus;
    }

    public String getCanonicalKey() {
        return canonicalKey;
    }

    public void setCanonicalKey(String canonicalKey) {
        this.canonicalKey = canonicalKey;
    }

    public String getVersionLabel() {
        return versionLabel;
    }

    public void setVersionLabel(String versionLabel) {
        this.versionLabel = versionLabel;
    }

    public int getAuthorityLevel() {
        return authorityLevel;
    }

    public void setAuthorityLevel(int authorityLevel) {
        this.authorityLevel = authorityLevel;
    }

    public String getJurisdiction() {
        return jurisdiction;
    }

    public void setJurisdiction(String jurisdiction) {
        this.jurisdiction = jurisdiction;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }
}
