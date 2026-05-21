package com.synapse.kb.repository;

import com.synapse.kb.model.ChatSession;
import com.synapse.kb.model.ChatSessionId;
import com.synapse.kb.model.KnowledgeBaseId;

import java.util.Optional;

/**
 * 聊天会话仓储接口。
 * 按 ownerUserId + knowledgeBaseId 隔离会话，保障用户聊天记忆的数据边界。
 */
public interface ChatSessionRepository {

    ChatSession save(ChatSession session);

    long nextMessageSequence(ChatSessionId id);

    Optional<ChatSession> findById(ChatSessionId id);

    Optional<ChatSession> findLatestByOwnerUserIdAndKnowledgeBaseId(String ownerUserId, KnowledgeBaseId knowledgeBaseId);
}
