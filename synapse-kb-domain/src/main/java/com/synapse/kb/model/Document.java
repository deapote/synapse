package com.synapse.kb.model;

import com.synapse.shared.exception.DomainException;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 文档聚合根。
 *
 * <p>文档是独立聚合根，通过 {@code knowledgeBaseId} 关联到知识库，避免大聚合性能问题。
 * 包含完整的处理生命周期：PENDING → PROCESSING → COMPLETED/FAILED，支持 FAILED → PENDING 重试。
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
    private Instant processingStartAt;
    private Instant processingCompleteAt;

    /**
     * 状态机规则
     */
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

    /**
     * 创建新的文档实例。
     *
     * @param knowledgeBaseId 所属知识库 ID
     * @param fileName        文件名，必填
     * @param fileType        文件类型
     * @param fileSize        文件大小（字节）
     * @param contentHash     内容哈希，用于去重
     * @return 状态为 PENDING 的新文档实例
     * @throws DomainException 文件名为空时抛出
     */
    public static Document create(KnowledgeBaseId knowledgeBaseId, String fileName, String fileType, long fileSize, String contentHash) {
        if (fileName == null || fileName.isBlank()) {
            throw new DomainException("文件名不能为空");
        }
        return new Document(DocumentId.generate(), knowledgeBaseId, fileName, fileType, fileSize, contentHash, Instant.now());
    }

    /**
     * 仅供仓储层重建聚合根使用。业务代码不应直接调用。
     *
     * <p>从持久化存储读取数据后，通过此方法完整还原领域对象的所有状态，
     * 包括处理状态、失败原因、分块数量和时间戳。使用直接字段赋值以避免
     * {@link #transitionTo} 的副作用（如覆盖 processingStartAt）。
     *
     * @param id                     文档唯一标识
     * @param knowledgeBaseId        所属知识库 ID
     * @param fileName               文件名
     * @param fileType               文件类型
     * @param fileSize               文件大小（字节）
     * @param contentHash            内容哈希
     * @param uploadedAt             上传时间
     * @param status                 处理状态
     * @param failureReason          失败原因
     * @param chunkCount             分块数量
     * @param processingStartAt      处理开始时间
     * @param processingCompleteAt   处理完成时间
     * @return 重建后的文档实例
     */
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
            Instant processingStartAt,
            Instant processingCompleteAt) {

        Document doc = new Document(id, knowledgeBaseId, fileName, fileType, fileSize, contentHash, uploadedAt);
        doc.status = status;
        doc.failureReason = failureReason;
        doc.chunkCount = chunkCount;
        doc.processingStartAt = processingStartAt;
        doc.processingCompleteAt = processingCompleteAt;
        return doc;
    }

    /**
     * 转换文档处理状态。
     *
     * @param newStatus 目标状态
     * @throws DomainException 非法状态流转时抛出
     */
    public void transitionTo(DocumentStatus newStatus) {
        Set<DocumentStatus> validNext = VALID_TRANSITIONS.get(this.status);
        if (validNext == null || !validNext.contains(newStatus)) {
            throw new DomainException("无效的状态转换 " + this.status + "->" + newStatus);
        }
        this.status = newStatus;
        if (newStatus == DocumentStatus.PROCESSING) {
            this.processingStartAt = Instant.now();
        }
        if (newStatus == DocumentStatus.COMPLETED || newStatus == DocumentStatus.FAILED) {
            this.processingCompleteAt = Instant.now();
        }
    }

    public void setFailureReason(String reason) {
        this.failureReason = reason;
    }

    /**
     * 设置文档分块数量。
     *
     * @param count 分块数，必须 ≥ 0
     * @throws DomainException 负数时抛出
     */
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

    public Instant getProcessingStartAt() {
        return processingStartAt;
    }

    public Instant getProcessingCompleteAt() {
        return processingCompleteAt;
    }
}
