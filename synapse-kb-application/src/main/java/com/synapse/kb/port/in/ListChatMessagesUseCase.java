package com.synapse.kb.port.in;

import com.synapse.kb.model.ChatMessage;
import com.synapse.kb.model.ChatSessionId;

import java.util.List;

public interface ListChatMessagesUseCase {

    List<ChatMessage> listChatMessages(ChatSessionId sessionId, int page, int size);
}
