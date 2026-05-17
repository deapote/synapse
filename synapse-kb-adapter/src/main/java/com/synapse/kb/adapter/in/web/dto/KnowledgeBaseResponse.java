package com.synapse.kb.adapter.in.web.dto;

import java.time.Instant;

public record KnowledgeBaseResponse(String id, String name, String description, String ownerUserId,
                                    Instant createdAt) {
}
