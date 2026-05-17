package com.synapse.kb.repository;

import com.synapse.kb.model.ChatSession;
import com.synapse.kb.model.ChatSessionId;
import com.synapse.kb.model.KnowledgeBaseId;

import java.util.Optional;

public interface ChatSessionRepository {

    ChatSession save(ChatSession session);

    Optional<ChatSession> findById(ChatSessionId id);

    Optional<ChatSession> findLatestByOwnerUserIdAndKnowledgeBaseId(String ownerUserId, KnowledgeBaseId knowledgeBaseId);
}
