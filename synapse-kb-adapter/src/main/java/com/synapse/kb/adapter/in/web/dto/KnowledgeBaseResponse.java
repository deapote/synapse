package com.synapse.kb.adapter.in.web.dto;

import java.time.Instant;

/**
 * 知识库响应 DTO。
 *
 * @param id          知识库唯一标识
 * @param name        知识库名称
 * @param description 知识库描述
 * @param createdAt   创建时间
 */
public record KnowledgeBaseResponse(String id, String name, String description, String ownerUserId,
                                    Instant createdAt) {
}
