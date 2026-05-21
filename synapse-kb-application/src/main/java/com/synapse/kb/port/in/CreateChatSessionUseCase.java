package com.synapse.kb.port.in;

import com.synapse.kb.model.ChatSession;
import com.synapse.kb.model.KnowledgeBaseId;

/**
 * 创建聊天会话用例。
 * 在指定知识库下为用户新建独立的聊天会话。
 */
public interface CreateChatSessionUseCase {

    ChatSession createChatSession(KnowledgeBaseId knowledgeBaseId);
}
