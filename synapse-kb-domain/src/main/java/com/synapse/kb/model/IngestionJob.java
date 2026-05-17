package com.synapse.kb.model;

import com.synapse.shared.exception.DomainException;

import java.time.Instant;
import java.util.UUID;

public class IngestionJob {
    private final String id;
    private final DocumentId documentId;
    private final KnowledgeBaseId knowledgeBaseId;
    private final String contentObjectId;
    private IngestionJobStatus status;
    private int attempts;
    private Instant nextRunAt;
    private String leaseOwner;
    private Instant leaseExpiresAt;
    private String failureReason;
    private final Instant createdAt;
    private Instant updatedAt;

    private IngestionJob(String id, DocumentId documentId, KnowledgeBaseId knowledgeBaseId,
                         String contentObjectId, IngestionJobStatus status, int attempts,
                         Instant nextRunAt, String leaseOwner, Instant leaseExpiresAt,
                         String failureReason, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.documentId = documentId;
        this.knowledgeBaseId = knowledgeBaseId;
        this.contentObjectId = contentObjectId;
        this.status = status;
        this.attempts = attempts;
        this.nextRunAt = nextRunAt;
        this.leaseOwner = leaseOwner;
        this.leaseExpiresAt = leaseExpiresAt;
        this.failureReason = failureReason;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static IngestionJob create(DocumentId documentId, KnowledgeBaseId knowledgeBaseId, String contentObjectId) {
        if (contentObjectId == null || contentObjectId.isBlank()) {
            throw new DomainException("摄入任务内容对象ID不能为空");
        }
        Instant now = Instant.now();
        return new IngestionJob(UUID.randomUUID().toString(), documentId, knowledgeBaseId, contentObjectId,
                IngestionJobStatus.QUEUED, 0, now, null, null, null, now, now);
    }

    public static IngestionJob reconstruct(String id, DocumentId documentId, KnowledgeBaseId knowledgeBaseId,
                                           String contentObjectId, IngestionJobStatus status, int attempts,
                                           Instant nextRunAt, String leaseOwner, Instant leaseExpiresAt,
                                           String failureReason, Instant createdAt, Instant updatedAt) {
        return new IngestionJob(id, documentId, knowledgeBaseId, contentObjectId, status, attempts, nextRunAt,
                leaseOwner, leaseExpiresAt, failureReason, createdAt, updatedAt);
    }

    public void markSucceeded() {
        this.status = IngestionJobStatus.SUCCEEDED;
        clearLease();
        touch();
    }

    public void markRetrying(String failureReason, Instant nextRunAt) {
        this.status = IngestionJobStatus.RETRYING;
        this.failureReason = failureReason;
        this.nextRunAt = nextRunAt;
        clearLease();
        touch();
    }

    public void markFailed(String failureReason) {
        this.status = IngestionJobStatus.FAILED;
        this.failureReason = failureReason;
        clearLease();
        touch();
    }

    public void clearLease() {
        this.leaseOwner = null;
        this.leaseExpiresAt = null;
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public DocumentId getDocumentId() {
        return documentId;
    }

    public KnowledgeBaseId getKnowledgeBaseId() {
        return knowledgeBaseId;
    }

    public String getContentObjectId() {
        return contentObjectId;
    }

    public IngestionJobStatus getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public Instant getNextRunAt() {
        return nextRunAt;
    }

    public String getLeaseOwner() {
        return leaseOwner;
    }

    public Instant getLeaseExpiresAt() {
        return leaseExpiresAt;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
