package com.synapse.kb.repository;

import com.synapse.kb.model.ChatMessage;
import com.synapse.kb.model.ChatSessionId;

import java.util.List;

/**
 * 聊天消息仓储接口。
 * 按 sessionId + sequence 排序查询，支持分页、最近消息及指定序列区间检索。
 */
public interface ChatMessageRepository {

    ChatMessage save(ChatMessage message);

    List<ChatMessage> findBySessionId(ChatSessionId sessionId, int page, int size);

    List<ChatMessage> findRecentBySessionIdBeforeOrEqual(ChatSessionId sessionId, long maxSequence, int limit);

    List<ChatMessage> findBySessionIdAndSequenceBetween(ChatSessionId sessionId, long fromExclusive, long toInclusive);
}
