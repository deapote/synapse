package com.synapse.kb.model;

import com.synapse.shared.exception.DomainException;

/**
 * 引用来源值对象。
 *
 * <p>表示 RAG 检索结果中的单个引用片段，用于前端展示来源和高亮定位。
 * 作为 {@link record} 实现，天然不可变、线程安全。
 *
 * @param documentId    来源文档唯一标识
 * @param documentName  来源文档文件名
 * @param chunkText     被引用的片段文本
 * @param score         相似度分数
 * @param startPosition 片段在原文中的起始字符位置（含）
 * @param endPosition   片段在原文中的结束字符位置（含）
 */
public record ChunkReference(
        String documentId,
        String documentName,
        String chunkText,
        double score,
        int startPosition,
        int endPosition
) {

    /**
     * 紧凑构造方法：在编译器自动完成字段赋值前执行校验。
     */
    public ChunkReference {
        if (documentId == null || documentId.isBlank()) {
            throw new DomainException("来源文档ID不能为空");
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
}
