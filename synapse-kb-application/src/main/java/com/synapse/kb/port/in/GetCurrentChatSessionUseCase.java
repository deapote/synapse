package com.synapse.kb.port.in;

import com.synapse.kb.model.ChatSession;
import com.synapse.kb.model.KnowledgeBaseId;

public interface GetCurrentChatSessionUseCase {

    ChatSession getOrCreateCurrentSession(KnowledgeBaseId knowledgeBaseId);
}
