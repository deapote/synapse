package com.synapse.kb.model;

import com.synapse.shared.exception.DomainException;

import java.time.Instant;
import java.util.UUID;

/**
 * 后台文档摄入任务。负责从 GridFS 读取原始内容、切分文本、生成向量并写入 Milvus 与 Mongo。
 * 采用租约（lease）机制防止多 worker 并发竞争同一任务。
 */
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

    /**
     * 创建新摄入任务。初始状态为 QUEUED，由调度器按创建时间顺序分派。
     */
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

    /** 标记摄入成功，清理租约。 */
    public void markSucceeded() {
        this.status = IngestionJobStatus.SUCCEEDED;
        clearLease();
        touch();
    }

    /**
     * 标记需重试。失败原因会被记录，任务将在 {@code nextRunAt} 之后再次可被认领。
     */
    public void markRetrying(String failureReason, Instant nextRunAt) {
        this.status = IngestionJobStatus.RETRYING;
        this.failureReason = failureReason;
        this.nextRunAt = nextRunAt;
        clearLease();
        touch();
    }

    /** 标记最终失败，不再自动重试，清理租约。 */
    public void markFailed(String failureReason) {
        this.status = IngestionJobStatus.FAILED;
        this.failureReason = failureReason;
        clearLease();
        touch();
    }

    /** 释放租约，使任务可被其他 worker 认领。 */
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
