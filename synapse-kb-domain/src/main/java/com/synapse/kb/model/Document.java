package com.synapse.kb.model;

import com.synapse.shared.exception.DomainException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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

    private DocumentSourceType sourceType;
    private String canonicalKey;
    private String versionLabel;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private DocumentLifecycleStatus lifecycleStatus;
    private String supersedesDocumentId;
    private int authorityLevel;
    private String jurisdiction;

    private long metadataVersion;
    private long indexedMetadataVersion;
    private DocumentIndexStatus indexStatus;
    private Instant lastIndexRefreshAt;
    private String lastIndexFailureReason;

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
        this.sourceType = DocumentSourceType.GENERAL;
        this.lifecycleStatus = DocumentLifecycleStatus.ACTIVE;
        this.effectiveFrom = LocalDate.ofInstant(uploadedAt, ZoneId.systemDefault());
        this.authorityLevel = 0;
        this.metadataVersion = 0;
        this.indexedMetadataVersion = 0;
        this.indexStatus = DocumentIndexStatus.SYNCED;
    }

    public static Document create(KnowledgeBaseId knowledgeBaseId, String fileName, String fileType, long fileSize, String contentHash) {
        return create(knowledgeBaseId, fileName, fileType, fileSize, contentHash, new DocumentMetadata());
    }

    public static Document create(KnowledgeBaseId knowledgeBaseId, String fileName, String fileType, long fileSize, String contentHash, DocumentMetadata metadata) {
        if (fileName == null || fileName.isBlank()) {
            throw new DomainException("文件名不能为空");
        }
        Document doc = new Document(DocumentId.generate(), knowledgeBaseId, fileName, fileType, fileSize, contentHash, Instant.now());
        if (metadata != null) {
            doc.applyMetadata(metadata);
        }
        return doc;
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
            Instant processingCompleteAt,
            DocumentSourceType sourceType,
            String canonicalKey,
            String versionLabel,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            DocumentLifecycleStatus lifecycleStatus,
            String supersedesDocumentId,
            Integer authorityLevel,
            String jurisdiction,
            Long metadataVersion,
            Long indexedMetadataVersion,
            DocumentIndexStatus indexStatus,
            Instant lastIndexRefreshAt,
            String lastIndexFailureReason) {

        Document doc = new Document(id, knowledgeBaseId, fileName, fileType, fileSize, contentHash, uploadedAt);
        doc.status = status;
        doc.failureReason = failureReason;
        doc.chunkCount = chunkCount;
        doc.contentObjectId = contentObjectId;
        doc.processingStartAt = processingStartAt;
        doc.processingCompleteAt = processingCompleteAt;
        doc.sourceType = sourceType != null ? sourceType : DocumentSourceType.GENERAL;
        doc.canonicalKey = canonicalKey;
        doc.versionLabel = versionLabel;
        doc.effectiveFrom = effectiveFrom != null ? effectiveFrom : LocalDate.ofInstant(uploadedAt, ZoneId.systemDefault());
        doc.effectiveTo = effectiveTo;
        doc.lifecycleStatus = lifecycleStatus != null ? lifecycleStatus : DocumentLifecycleStatus.ACTIVE;
        doc.supersedesDocumentId = supersedesDocumentId;
        doc.authorityLevel = authorityLevel != null ? authorityLevel : 0;
        doc.jurisdiction = jurisdiction;
        doc.metadataVersion = metadataVersion != null ? metadataVersion : 0;
        doc.indexedMetadataVersion = indexedMetadataVersion != null ? indexedMetadataVersion : 0;
        doc.indexStatus = indexStatus != null ? indexStatus : DocumentIndexStatus.SYNCED;
        doc.lastIndexRefreshAt = lastIndexRefreshAt;
        doc.lastIndexFailureReason = lastIndexFailureReason;
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

    public boolean isEffectiveOn(LocalDate date) {
        if (date == null) {
            return false;
        }
        if (lifecycleStatus == DocumentLifecycleStatus.RETIRED) {
            return false;
        }
        if (effectiveFrom != null && date.isBefore(effectiveFrom)) {
            return false;
        }
        if (effectiveTo != null && !date.isBefore(effectiveTo)) {
            return false;
        }
        return true;
    }

    public void updateMetadata(DocumentMetadata metadata) {
        if (metadata == null) {
            return;
        }
        applyMetadata(metadata);
    }

    private void applyMetadata(DocumentMetadata metadata) {
        if (metadata.sourceType() != null) {
            this.sourceType = metadata.sourceType();
        }
        if (metadata.canonicalKey() != null) {
            this.canonicalKey = metadata.canonicalKey();
        }
        if (metadata.versionLabel() != null) {
            this.versionLabel = metadata.versionLabel();
        }
        if (metadata.effectiveFrom() != null) {
            this.effectiveFrom = metadata.effectiveFrom();
        }
        if (metadata.effectiveTo() != null) {
            this.effectiveTo = metadata.effectiveTo();
        }
        validateEffectiveDates();
        if (metadata.supersedesDocumentId() != null) {
            this.supersedesDocumentId = metadata.supersedesDocumentId();
        }
        if (metadata.authorityLevel() != null) {
            this.authorityLevel = metadata.authorityLevel();
        }
        if (metadata.jurisdiction() != null) {
            this.jurisdiction = metadata.jurisdiction();
        }
    }

    private void validateEffectiveDates() {
        if (this.effectiveFrom != null && this.effectiveTo != null
                && !this.effectiveTo.isAfter(this.effectiveFrom)) {
            throw new DomainException("失效日期必须晚于生效日期");
        }
    }

    public void activate() {
        this.lifecycleStatus = DocumentLifecycleStatus.ACTIVE;
    }

    public void retire() {
        this.lifecycleStatus = DocumentLifecycleStatus.RETIRED;
    }

    public void retire(LocalDate effectiveTo) {
        if (effectiveTo != null) {
            this.effectiveTo = effectiveTo;
        }
        this.lifecycleStatus = DocumentLifecycleStatus.RETIRED;
    }

    public void reactivate() {
        this.lifecycleStatus = DocumentLifecycleStatus.ACTIVE;
        this.effectiveTo = null;
    }

    public void patchMetadata(PatchDocumentMetadata patch) {
        if (patch == null) {
            return;
        }

        DocumentSourceType candidateSourceType = this.sourceType;
        String candidateCanonicalKey = this.canonicalKey;
        String candidateVersionLabel = this.versionLabel;
        LocalDate candidateEffectiveFrom = this.effectiveFrom;
        LocalDate candidateEffectiveTo = this.effectiveTo;
        Integer candidateAuthorityLevel = this.authorityLevel;
        String candidateJurisdiction = this.jurisdiction;

        if (patch.sourceType().isPresent()) {
            if (patch.sourceType().isClear()) {
                throw new DomainException("资料类型不允许清空");
            }
            candidateSourceType = patch.sourceType().value();
        }
        if (patch.canonicalKey().isPresent()) {
            candidateCanonicalKey = patch.canonicalKey().isClear() ? null : patch.canonicalKey().value();
        }
        if (patch.versionLabel().isPresent()) {
            candidateVersionLabel = patch.versionLabel().isClear() ? null : patch.versionLabel().value();
        }
        if (patch.effectiveFrom().isPresent()) {
            if (patch.effectiveFrom().isClear()) {
                throw new DomainException("生效日期不允许清空");
            }
            candidateEffectiveFrom = patch.effectiveFrom().value();
        }
        if (patch.effectiveTo().isPresent()) {
            candidateEffectiveTo = patch.effectiveTo().isClear() ? null : patch.effectiveTo().value();
        }
        if (patch.authorityLevel().isPresent()) {
            candidateAuthorityLevel = patch.authorityLevel().isClear() ? 0 : patch.authorityLevel().value();
        }
        if (patch.jurisdiction().isPresent()) {
            candidateJurisdiction = patch.jurisdiction().isClear() ? null : patch.jurisdiction().value();
        }

        if (candidateEffectiveFrom != null && candidateEffectiveTo != null
                && !candidateEffectiveTo.isAfter(candidateEffectiveFrom)) {
            throw new DomainException("失效日期必须晚于生效日期");
        }
        if (candidateAuthorityLevel != null && candidateAuthorityLevel < 0) {
            throw new DomainException("权威等级不能为负数");
        }
        if (candidateSourceType == null) {
            throw new DomainException("资料类型不能为空");
        }

        this.sourceType = candidateSourceType;
        this.canonicalKey = candidateCanonicalKey;
        this.versionLabel = candidateVersionLabel;
        this.effectiveFrom = candidateEffectiveFrom;
        this.effectiveTo = candidateEffectiveTo;
        this.authorityLevel = candidateAuthorityLevel;
        this.jurisdiction = candidateJurisdiction;
    }

    public void markIndexStale() {
        this.indexStatus = DocumentIndexStatus.STALE;
        this.metadataVersion++;
    }

    public void markIndexRefreshing() {
        this.indexStatus = DocumentIndexStatus.REFRESHING;
        this.lastIndexFailureReason = null;
    }

    public void markIndexSynced() {
        this.indexStatus = DocumentIndexStatus.SYNCED;
        this.indexedMetadataVersion = this.metadataVersion;
        this.lastIndexRefreshAt = Instant.now();
        this.lastIndexFailureReason = null;
    }

    public void markIndexFailed(String reason) {
        this.indexStatus = DocumentIndexStatus.FAILED;
        this.lastIndexFailureReason = reason;
    }

    public boolean needsIndexRefresh() {
        return this.indexStatus == DocumentIndexStatus.STALE || this.indexStatus == DocumentIndexStatus.FAILED;
    }

    public void supersedeBy(Document newDocument, LocalDate effectiveTo) {
        if (newDocument == null) {
            throw new DomainException("替代文档不能为空");
        }
        if (!this.knowledgeBaseId.equals(newDocument.knowledgeBaseId)) {
            throw new DomainException("被替代文档与替代文档必须属于同一知识库");
        }
        if (this.lifecycleStatus != DocumentLifecycleStatus.ACTIVE) {
            throw new DomainException("仅 ACTIVE 文档可被替代");
        }
        if (effectiveTo == null) {
            throw new DomainException("替代失效日期不能为空");
        }
        if (this.effectiveFrom != null && effectiveTo.isBefore(this.effectiveFrom)) {
            throw new DomainException("替代失效日期不得早于文档生效日期");
        }
        this.effectiveTo = effectiveTo;
        this.lifecycleStatus = DocumentLifecycleStatus.SUPERSEDED;
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

    public DocumentSourceType getSourceType() {
        return sourceType;
    }

    public String getCanonicalKey() {
        return canonicalKey;
    }

    public String getVersionLabel() {
        return versionLabel;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public LocalDate getEffectiveTo() {
        return effectiveTo;
    }

    public DocumentLifecycleStatus getLifecycleStatus() {
        return lifecycleStatus;
    }

    public String getSupersedesDocumentId() {
        return supersedesDocumentId;
    }

    public int getAuthorityLevel() {
        return authorityLevel;
    }

    public String getJurisdiction() {
        return jurisdiction;
    }

    public long getMetadataVersion() {
        return metadataVersion;
    }

    public long getIndexedMetadataVersion() {
        return indexedMetadataVersion;
    }

    public DocumentIndexStatus getIndexStatus() {
        return indexStatus;
    }

    public Instant getLastIndexRefreshAt() {
        return lastIndexRefreshAt;
    }

    public String getLastIndexFailureReason() {
        return lastIndexFailureReason;
    }
}
