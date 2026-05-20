package com.synapse.kb.model;

import com.synapse.shared.exception.DomainException;

import java.time.Instant;

/**
 * 文档索引刷新任务。
 */
public class DocumentIndexRefreshJob {

    private final DocumentIndexRefreshJobId id;
    private final DocumentId documentId;
    private final KnowledgeBaseId knowledgeBaseId;
    private final long metadataVersion;
    private DocumentIndexRefreshJobStatus status;
    private int attempts;
    private String failureReason;
    private Instant nextRunAt;
    private final Instant createdAt;
    private Instant updatedAt;

    private DocumentIndexRefreshJob(DocumentIndexRefreshJobId id, DocumentId documentId,
                                     KnowledgeBaseId knowledgeBaseId, long metadataVersion,
                                     Instant createdAt) {
        this.id = id;
        this.documentId = documentId;
        this.knowledgeBaseId = knowledgeBaseId;
        this.metadataVersion = metadataVersion;
        this.status = DocumentIndexRefreshJobStatus.PENDING;
        this.attempts = 0;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public static DocumentIndexRefreshJob create(DocumentId documentId,
                                                  KnowledgeBaseId knowledgeBaseId,
                                                  long metadataVersion) {
        return new DocumentIndexRefreshJob(
                DocumentIndexRefreshJobId.generate(),
                documentId,
                knowledgeBaseId,
                metadataVersion,
                Instant.now()
        );
    }

    public static DocumentIndexRefreshJob reconstruct(DocumentIndexRefreshJobId id,
                                                       DocumentId documentId,
                                                       KnowledgeBaseId knowledgeBaseId,
                                                       long metadataVersion,
                                                       DocumentIndexRefreshJobStatus status,
                                                       int attempts,
                                                       String failureReason,
                                                       Instant nextRunAt,
                                                       Instant createdAt,
                                                       Instant updatedAt) {
        DocumentIndexRefreshJob job = new DocumentIndexRefreshJob(id, documentId, knowledgeBaseId,
                metadataVersion, createdAt);
        job.status = status;
        job.attempts = attempts;
        job.failureReason = failureReason;
        job.nextRunAt = nextRunAt;
        job.updatedAt = updatedAt;
        return job;
    }

    public void markRunning() {
        if (this.status != DocumentIndexRefreshJobStatus.PENDING && this.status != DocumentIndexRefreshJobStatus.FAILED) {
            throw new DomainException("仅 PENDING 或 FAILED 任务可标记为 RUNNING");
        }
        this.status = DocumentIndexRefreshJobStatus.RUNNING;
        this.attempts++;
        this.updatedAt = Instant.now();
    }

    public void markSucceeded() {
        if (this.status != DocumentIndexRefreshJobStatus.RUNNING) {
            throw new DomainException("仅 RUNNING 任务可标记为 SUCCEEDED");
        }
        this.status = DocumentIndexRefreshJobStatus.SUCCEEDED;
        this.failureReason = null;
        this.updatedAt = Instant.now();
    }

    public void markFailed(String reason, Instant nextRunAt) {
        if (this.status != DocumentIndexRefreshJobStatus.RUNNING) {
            throw new DomainException("仅 RUNNING 任务可标记为 FAILED");
        }
        this.status = DocumentIndexRefreshJobStatus.FAILED;
        this.failureReason = reason;
        this.nextRunAt = nextRunAt;
        this.updatedAt = Instant.now();
    }

    public boolean isClaimableAt(Instant now) {
        return this.status == DocumentIndexRefreshJobStatus.PENDING
                || (this.status == DocumentIndexRefreshJobStatus.FAILED
                && this.nextRunAt != null
                && !now.isBefore(this.nextRunAt));
    }

    public DocumentIndexRefreshJobId getId() {
        return id;
    }

    public DocumentId getDocumentId() {
        return documentId;
    }

    public KnowledgeBaseId getKnowledgeBaseId() {
        return knowledgeBaseId;
    }

    public long getMetadataVersion() {
        return metadataVersion;
    }

    public DocumentIndexRefreshJobStatus getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getNextRunAt() {
        return nextRunAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
