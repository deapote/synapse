package com.synapse.kb.adapter.in.web.dto;

/**
 * 引用来源响应 DTO。
 *
 * @param documentId    来源文档 ID
 * @param documentName  来源文档文件名
 * @param chunkText     被引用的片段文本
 * @param score         相似度分数，范围 [0, 1]
 * @param startPosition 片段在原文中的起始位置
 * @param endPosition   片段在原文中的结束位置
 */
public record ChunkReferenceResponse(String documentId, String documentName, String chunkText,
                                     float score, int startPosition, int endPosition) {
}
