package com.synapse.kb.model;

import com.synapse.shared.exception.DomainException;

import java.time.LocalDate;

/** RAG 检索引用片段。 */
public record ChunkReference(
        String documentId,
        String documentName,
        int chunkIndex,
        String chunkText,
        float score,
        int startPosition,
        int endPosition,
        String canonicalKey,
        String versionLabel,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        DocumentLifecycleStatus lifecycleStatus,
        int authorityLevel,
        String jurisdiction
) {

    public ChunkReference {
        if (documentId == null || documentId.isBlank()) {
            throw new DomainException("来源文档ID不能为空");
        }
        if (chunkIndex < 0) {
            throw new DomainException("分块序号不能为空");
        }
        if (chunkText == null || chunkText.isBlank()) {
            throw new DomainException("引用文本不能为空");
        }
        if (score < 0 || score > 1) {
            throw new DomainException("相似度分数必须在 [0, 1] 范围内");
        }
        if (startPosition < 0 || endPosition < startPosition) {
            throw new DomainException("无效的位置范围");
        }
    }

    /** 向后兼容：不包含时效字段的构造。 */
    public ChunkReference(String documentId, String documentName, int chunkIndex,
                          String chunkText, float score, int startPosition, int endPosition) {
        this(documentId, documentName, chunkIndex, chunkText, score, startPosition, endPosition,
                null, null, null, null, null, 0, null);
    }
}
