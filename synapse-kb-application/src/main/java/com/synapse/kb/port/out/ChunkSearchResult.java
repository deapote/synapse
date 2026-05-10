package com.synapse.kb.port.out;

import com.synapse.shared.exception.DomainException;

/**
 * 向量检索结果。
 *
 * <p>封装 {@link VectorStorePort#search} 返回的单条检索结果，
 * 包含来源文档、片段文本、相似度分数及原文位置信息。
 *
 * @param documentId    来源文档 ID
 * @param chunkText     检索到的片段文本
 * @param score         相似度分数，范围 [0, 1]，越接近 1 越相似
 * @param startPosition 片段在原文中的起始字符位置
 * @param endPosition   片段在原文中的结束字符位置
 */
public record ChunkSearchResult(
        String documentId,
        String chunkText,
        float score,
        int startPosition,
        int endPosition
) {

    /**
     * 紧凑构造方法：在编译器自动完成字段赋值前执行校验。
     */
    public ChunkSearchResult {
        if (documentId == null || documentId.isBlank()) {
            throw new DomainException("来源文档ID不能为空");
        }
        if (chunkText == null || chunkText.isBlank()) {
            throw new DomainException("检索片段文本不能为空");
        }
        if (score < 0 || score > 1) {
            throw new DomainException("相似度分数必须在 [0, 1] 范围内");
        }
        if (startPosition < 0 || endPosition < startPosition) {
            throw new DomainException("无效的位置范围");
        }
    }
}
