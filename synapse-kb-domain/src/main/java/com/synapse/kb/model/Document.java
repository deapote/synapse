package com.synapse.kb.model;

import com.synapse.shared.exception.DomainException;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 文档聚合根。文档独立于知识库聚合，通过 {@code knowledgeBaseId} 关联。
 */
public class Document {
    private final DocumentId id;
    private final KnowledgeBaseId knowledgeBaseId;
    private String fileName;
    private String fileType;
    private long fileSize;
    private final Instant uploadedAt;
    private DocumentStatus status;
    private String failureReason;
    private int chunkCount;
    private String contentHash;
    private String contentObjectId;
    private Instant processingStartAt;
    private Instant processingCompleteAt;

    /** 文档处理状态机规则。 */
    private static final Map<DocumentStatus, Set<DocumentStatus>> VALID_TRANSITIONS = Map.of(
            DocumentStatus.PENDING, EnumSet.of(DocumentStatus.PROCESSING),
            DocumentStatus.PROCESSING, EnumSet.of(DocumentStatus.COMPLETED, DocumentStatus.FAILED),
            DocumentStatus.FAILED, EnumSet.of(DocumentStatus.PENDING),
            DocumentStatus.COMPLETED, EnumSet.noneOf(DocumentStatus.class)
    );

    private Document(DocumentId id, KnowledgeBaseId knowledgeBaseId, String fileName, String fileType, long fileSize, String contentHash, Instant uploadedAt) {
        this.id = id;
        this.knowledgeBaseId = knowledgeBaseId;
        this.fileName = fileName;
        this.fileType = fileType;
        this.fileSize = fileSize;
        this.contentHash = contentHash;
        this.uploadedAt = uploadedAt;
        this.status = DocumentStatus.PENDING;
        this.chunkCount = 0;
    }

    public static Document create(KnowledgeBaseId knowledgeBaseId, String fileName, String fileType, long fileSize, String contentHash) {
        if (fileName == null || fileName.isBlank()) {
            throw new DomainException("文件名不能为空");
        }
        return new Document(DocumentId.generate(), knowledgeBaseId, fileName, fileType, fileSize, contentHash, Instant.now());
    }

    public void attachContentObject(String contentObjectId) {
        if (contentObjectId == null || contentObjectId.isBlank()) {
            throw new DomainException("文档内容对象ID不能为空");
        }
        this.contentObjectId = contentObjectId;
    }

    /** 仅供仓储层重建聚合根使用，避免触发状态机副作用。 */
    public static Document reconstruct(
            DocumentId id,
            KnowledgeBaseId knowledgeBaseId,
            String fileName,
            String fileType,
            long fileSize,
            String contentHash,
            Instant uploadedAt,
            DocumentStatus status,
            String failureReason,
            int chunkCount,
            String contentObjectId,
            Instant processingStartAt,
            Instant processingCompleteAt) {

        Document doc = new Document(id, knowledgeBaseId, fileName, fileType, fileSize, contentHash, uploadedAt);
        doc.status = status;
        doc.failureReason = failureReason;
        doc.chunkCount = chunkCount;
        doc.contentObjectId = contentObjectId;
        doc.processingStartAt = processingStartAt;
        doc.processingCompleteAt = processingCompleteAt;
        return doc;
    }

    public void transitionTo(DocumentStatus newStatus) {
        transitionTo(newStatus, null);
    }

    public void transitionTo(DocumentStatus newStatus, String failureReason) {
        Set<DocumentStatus> validNext = VALID_TRANSITIONS.get(this.status);
        if (validNext == null || !validNext.contains(newStatus)) {
            throw new DomainException("无效的状态转换 " + this.status + "->" + newStatus);
        }
        this.status = newStatus;
        if (newStatus == DocumentStatus.PROCESSING) {
            this.processingStartAt = Instant.now();
            this.processingCompleteAt = null;
            this.failureReason = null;
        }
        if (newStatus == DocumentStatus.COMPLETED || newStatus == DocumentStatus.FAILED) {
            this.processingCompleteAt = Instant.now();
        }
        if (newStatus == DocumentStatus.FAILED && failureReason != null) {
            this.failureReason = failureReason;
        }
    }

    public void retry() {
        transitionTo(DocumentStatus.PENDING);
        this.processingStartAt = null;
        this.processingCompleteAt = null;
        this.failureReason = null;
        this.chunkCount = 0;
    }

    public void setChunkCount(int count) {
        if (count < 0) {
            throw new DomainException("块数不能为负值");
        }
        this.chunkCount = count;
    }

    public DocumentId getId() {
        return id;
    }

    public KnowledgeBaseId getKnowledgeBaseId() {
        return knowledgeBaseId;
    }

    public String getFileName() {
        return fileName;
    }

    public String getFileType() {
        return fileType;
    }

    public long getFileSize() {
        return fileSize;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }

    public DocumentStatus getStatus() {
        return status;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public int getChunkCount() {
        return chunkCount;
    }

    public String getContentHash() {
        return contentHash;
    }

    public String getContentObjectId() {
        return contentObjectId;
    }

    public Instant getProcessingStartAt() {
        return processingStartAt;
    }

    public Instant getProcessingCompleteAt() {
        return processingCompleteAt;
    }
}
