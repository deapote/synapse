package com.synapse.kb.port.in;

import com.synapse.kb.model.ChatSession;
import com.synapse.kb.model.KnowledgeBaseId;

/**
 * 获取当前聊天会话用例。
 * 返回用户在指定知识库下最新的会话，不存在时自动创建。
 */
public interface GetCurrentChatSessionUseCase {

    ChatSession getOrCreateCurrentSession(KnowledgeBaseId knowledgeBaseId);
}
