package com.synapse.kb.port.out;

import com.synapse.kb.model.ChatMessage;

import java.util.List;

public interface ChatMemorySummarizerPort {

    String summarize(String existingSummary, List<ChatMessage> messages, int maxChars);
}
