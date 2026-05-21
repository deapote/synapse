package com.synapse.kb.port.in;

import com.synapse.kb.model.ChatMessage;
import com.synapse.kb.model.ChatSessionId;

import java.util.List;

/**
 * 分页查询聊天消息用例。
 * 按会话 ID 返回历史消息列表，按 sequence 排序。
 */
public interface ListChatMessagesUseCase {

    List<ChatMessage> listChatMessages(ChatSessionId sessionId, int page, int size);
}
