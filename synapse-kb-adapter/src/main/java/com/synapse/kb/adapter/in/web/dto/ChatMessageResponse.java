package com.synapse.kb.adapter.in.web.dto;

import java.time.Instant;
import java.util.List;

/**
 * 聊天消息响应 DTO。
 */
public record ChatMessageResponse(
        String id,
        String sessionId,
        String role,
        String content,
        List<ChunkReferenceResponse> references,
        long sequence,
        Instant createdAt
) {
}
