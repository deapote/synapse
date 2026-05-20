package com.synapse.kb.model;

import com.synapse.shared.exception.DomainException;

import java.time.Instant;

/**
 * 文档索引刷新任务。当文档 metadata 被 patch / retire / reactivate / supersede 后，
 * 由领域事件触发，负责将 Mongo 权威状态同步到 Milvus 向量索引与 Mongo BM25 关键词索引。
 * 失败后可按指数退避重试，确保索引最终一致性。
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

    /**
     * 创建刷新任务。{@code metadataVersion} 必须与文档当前版本一致，
     * 防止旧版本任务覆盖新版本的索引状态。
     */
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

    /**
     * worker 认领任务时调用。FAILED 任务可被重新认领（重试语义），
     * 每次认领增加 attempts 计数，用于指数退避计算下次执行时间。
     */
    public void markRunning() {
        if (this.status != DocumentIndexRefreshJobStatus.PENDING && this.status != DocumentIndexRefreshJobStatus.FAILED) {
            throw new DomainException("仅 PENDING 或 FAILED 任务可标记为 RUNNING");
        }
        this.status = DocumentIndexRefreshJobStatus.RUNNING;
        this.attempts++;
        this.updatedAt = Instant.now();
    }

    /** 索引刷新成功后标记完成，清除失败原因。 */
    public void markSucceeded() {
        if (this.status != DocumentIndexRefreshJobStatus.RUNNING) {
            throw new DomainException("仅 RUNNING 任务可标记为 SUCCEEDED");
        }
        this.status = DocumentIndexRefreshJobStatus.SUCCEEDED;
        this.failureReason = null;
        this.updatedAt = Instant.now();
    }

    /**
     * 标记刷新失败并设置下次重试时间。FAILED 任务在 {@code nextRunAt} 到达后可被重新认领，
     * 实现指数退避重试，避免持续失败的索引抖动。
     */
    public void markFailed(String reason, Instant nextRunAt) {
        if (this.status != DocumentIndexRefreshJobStatus.RUNNING) {
            throw new DomainException("仅 RUNNING 任务可标记为 FAILED");
        }
        this.status = DocumentIndexRefreshJobStatus.FAILED;
        this.failureReason = reason;
        this.nextRunAt = nextRunAt;
        this.updatedAt = Instant.now();
    }

    /**
     * 判断任务在指定时刻是否可被 worker 认领。
     * PENDING 任务随时可认领；FAILED 任务需等待退避时间过后。
     */
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
