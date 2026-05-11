package com.synapse.kb.adapter.out.persistence.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "documents")
public class DocumentDocument {
    @Id
    private String id;

    private String knowledgeBaseId;

    private String fileName;

    private String fileType;

    private long fileSize;

    private Instant uploadedAt;

    private String status;

    private String failureReason;

    private int chunkCount;

    private String contentHash;

    private Instant processingStartAt;

    private Instant processingCompleteAt;

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
}
