package com.synapse.kb.adapter.in.web.dto;

import java.time.Instant;

/**
 * 知识库响应 DTO。
 */
public record KnowledgeBaseResponse(String id, String name, String description, String ownerUserId,
                                    Instant createdAt) {
}
