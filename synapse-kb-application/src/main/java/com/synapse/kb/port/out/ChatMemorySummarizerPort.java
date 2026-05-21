package com.synapse.kb.port.out;

import com.synapse.kb.model.ChatMessage;

import java.util.List;

/**
 * 聊天记忆摘要端口，由 LLM 适配器实现。
 * 对历史消息进行压缩摘要，控制上下文长度。
 */
public interface ChatMemorySummarizerPort {

    String summarize(String existingSummary, List<ChatMessage> messages, int maxChars);
}
