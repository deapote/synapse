package com.synapse.kb.repository;

import com.synapse.kb.model.ChatMessage;
import com.synapse.kb.model.ChatSessionId;

import java.util.List;

public interface ChatMessageRepository {

    ChatMessage save(ChatMessage message);

    List<ChatMessage> findBySessionId(ChatSessionId sessionId, int page, int size);

    List<ChatMessage> findRecentBySessionIdBeforeOrEqual(ChatSessionId sessionId, long maxSequence, int limit);

    List<ChatMessage> findBySessionIdAndSequenceBetween(ChatSessionId sessionId, long fromExclusive, long toInclusive);
}
