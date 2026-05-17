package com.synapse.kb.port.in;

import com.synapse.kb.model.ChatSession;
import com.synapse.kb.model.KnowledgeBaseId;

public interface CreateChatSessionUseCase {

    ChatSession createChatSession(KnowledgeBaseId knowledgeBaseId);
}
