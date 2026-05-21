package com.synapse.kb.adapter.in.web.dto;

import java.time.Instant;

/**
 * 聊天会话响应 DTO。
 */
public record ChatSessionResponse(
        String id,
        String knowledgeBaseId,
        String title,
        String summary,
        long messageCount,
        Instant createdAt,
        Instant updatedAt
) {
}
