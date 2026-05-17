package com.synapse.kb.model;

import com.synapse.shared.exception.DomainException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 单条聊天消息，按 session 内 sequence 保持顺序。 */
public record ChatMessage(
        String id,
        ChatSessionId sessionId,
        String ownerUserId,
        KnowledgeBaseId knowledgeBaseId,
        ChatRole role,
        String content,
        List<ChunkReference> references,
        long sequence,
        Instant createdAt
) {

    public ChatMessage {
        if (id == null || id.isBlank()) {
            throw new DomainException("聊天消息编号不能为空");
        }
        if (sessionId == null) {
            throw new DomainException("聊天会话编号不能为空");
        }
        if (ownerUserId == null || ownerUserId.isBlank()) {
            throw new DomainException("消息所属用户不能为空");
        }
        if (knowledgeBaseId == null) {
            throw new DomainException("知识库编号不能为空");
        }
        if (role == null) {
            throw new DomainException("消息角色不能为空");
        }
        if (content == null || content.isBlank()) {
            throw new DomainException("消息内容不能为空");
        }
        references = references == null ? List.of() : List.copyOf(references);
        if (sequence <= 0) {
            throw new DomainException("消息序号必须大于 0");
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public static ChatMessage create(
            ChatSession session,
            ChatRole role,
            String content,
            List<ChunkReference> references
    ) {
        return new ChatMessage(
                UUID.randomUUID().toString(),
                session.getId(),
                session.getOwnerUserId(),
                session.getKnowledgeBaseId(),
                role,
                content,
                references,
                session.nextSequence(),
                Instant.now()
        );
    }
}
