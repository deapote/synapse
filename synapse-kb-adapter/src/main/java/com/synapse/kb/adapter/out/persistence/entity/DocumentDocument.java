package com.synapse.kb.adapter.out.persistence.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.time.LocalDate;

@Document(collection = "documents")
@CompoundIndexes({
        @CompoundIndex(name = "uk_kb_content_hash", def = "{'knowledgeBaseId': 1, 'contentHash': 1}", unique = true),
        @CompoundIndex(name = "idx_kb_canonical_lifecycle", def = "{'knowledgeBaseId': 1, 'canonicalKey': 1, 'lifecycleStatus': 1}"),
        @CompoundIndex(name = "idx_kb_effective_dates", def = "{'knowledgeBaseId': 1, 'effectiveFrom': 1, 'effectiveTo': 1}")
})
public class DocumentDocument {
    @Id
    private String id;

    @Indexed
    private String knowledgeBaseId;

    private String fileName;

    private String fileType;

    private long fileSize;

    @Indexed
    private Instant uploadedAt;

    private String status;

    private String failureReason;

    private int chunkCount;

    @Indexed
    private String contentHash;

    private String contentObjectId;

    private Instant processingStartAt;

    private Instant processingCompleteAt;

    private String sourceType;

    private String canonicalKey;

    private String versionLabel;

    private LocalDate effectiveFrom;

    private LocalDate effectiveTo;

    private String lifecycleStatus;

    private String supersedesDocumentId;

    private Integer authorityLevel;

    private String jurisdiction;

    private Long metadataVersion;

    private Long indexedMetadataVersion;

    private String indexStatus;

    private Instant lastIndexRefreshAt;

    private String lastIndexFailureReason;

    public DocumentDocument() {}

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

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(Instant uploadAt) {
        this.uploadedAt = uploadAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public int getChunkCount() {
        return chunkCount;
    }

    public void setChunkCount(int chunkCount) {
        this.chunkCount = chunkCount;
    }

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    public String getContentObjectId() {
        return contentObjectId;
    }

    public void setContentObjectId(String contentObjectId) {
        this.contentObjectId = contentObjectId;
    }

    public Instant getProcessingStartAt() {
        return processingStartAt;
    }

    public void setProcessingStartAt(Instant processingStartAt) {
        this.processingStartAt = processingStartAt;
    }

    public Instant getProcessingCompleteAt() {
        return processingCompleteAt;
    }

    public void setProcessingCompleteAt(Instant processingCompleteAt) {
        this.processingCompleteAt = processingCompleteAt;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
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

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public void setEffectiveFrom(LocalDate effectiveFrom) {
        this.effectiveFrom = effectiveFrom;
    }

    public LocalDate getEffectiveTo() {
        return effectiveTo;
    }

    public void setEffectiveTo(LocalDate effectiveTo) {
        this.effectiveTo = effectiveTo;
    }

    public String getLifecycleStatus() {
        return lifecycleStatus;
    }

    public void setLifecycleStatus(String lifecycleStatus) {
        this.lifecycleStatus = lifecycleStatus;
    }

    public String getSupersedesDocumentId() {
        return supersedesDocumentId;
    }

    public void setSupersedesDocumentId(String supersedesDocumentId) {
        this.supersedesDocumentId = supersedesDocumentId;
    }

    public Integer getAuthorityLevel() {
        return authorityLevel;
    }

    public void setAuthorityLevel(Integer authorityLevel) {
        this.authorityLevel = authorityLevel;
    }

    public String getJurisdiction() {
        return jurisdiction;
    }

    public void setJurisdiction(String jurisdiction) {
        this.jurisdiction = jurisdiction;
    }

    public Long getMetadataVersion() {
        return metadataVersion;
    }

    public void setMetadataVersion(Long metadataVersion) {
        this.metadataVersion = metadataVersion;
    }

    public Long getIndexedMetadataVersion() {
        return indexedMetadataVersion;
    }

    public void setIndexedMetadataVersion(Long indexedMetadataVersion) {
        this.indexedMetadataVersion = indexedMetadataVersion;
    }

    public String getIndexStatus() {
        return indexStatus;
    }

    public void setIndexStatus(String indexStatus) {
        this.indexStatus = indexStatus;
    }

    public Instant getLastIndexRefreshAt() {
        return lastIndexRefreshAt;
    }

    public void setLastIndexRefreshAt(Instant lastIndexRefreshAt) {
        this.lastIndexRefreshAt = lastIndexRefreshAt;
    }

    public String getLastIndexFailureReason() {
        return lastIndexFailureReason;
    }

    public void setLastIndexFailureReason(String lastIndexFailureReason) {
        this.lastIndexFailureReason = lastIndexFailureReason;
    }
}
