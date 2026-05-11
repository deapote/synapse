package com.synapse.kb.adapter.in.web.dto;

import java.time.Instant;

/**
 * 文档响应 DTO。
 *
 * @param id            文档唯一标识
 * @param knowledgeBaseId 所属知识库 ID
 * @param fileName      原始文件名
 * @param fileType      文件 MIME 类型
 * @param fileSize      文件大小（字节）
 * @param status        处理状态：PENDING / PROCESSING / COMPLETED / FAILED
 * @param chunkCount    分块数量
 * @param uploadedAt    上传时间
 */
public record DocumentResponse(
        String id, String knowledgeBaseId, String fileName, String fileType,
        long fileSize, String status, int chunkCount, Instant uploadedAt
) {
}
