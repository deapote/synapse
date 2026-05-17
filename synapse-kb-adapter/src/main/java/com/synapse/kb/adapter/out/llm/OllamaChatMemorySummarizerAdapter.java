package com.synapse.kb.adapter.out.llm;

import com.synapse.kb.model.ChatMessage;
import com.synapse.kb.model.ChatRole;
import com.synapse.kb.port.out.ChatMemorySummarizerPort;
import com.synapse.shared.exception.DomainException;
import dev.langchain4j.model.ollama.OllamaChatModel;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/** Ollama 聊天记忆摘要适配器。 */
@Component
public class OllamaChatMemorySummarizerAdapter implements ChatMemorySummarizerPort {

    private final OllamaChatModel chatModel;
    private final String promptTemplate;

    public OllamaChatMemorySummarizerAdapter(
            @Value("${ollama.base-url:http://localhost:11434}") String baseUrl,
            @Value("${ollama.chat-model:qwen2.5:7b}") String modelName,
            @Value("${ollama.chat-timeout-seconds:120}") long timeoutSeconds,
            @Value("${synapse.chat-memory.summary-prompt-template:你是对话记忆压缩器。请基于已有摘要和新增对话，生成一段紧凑、事实准确的中文记忆摘要。保留用户目标、约束、已确认结论和待办；不要加入未出现的信息；不要输出解释。\n\n已有摘要：\n%s\n\n新增对话：\n%s\n\n摘要字数不超过 %d 字。}") String promptTemplate
    ) {
        this.chatModel = OllamaChatModel.builder()
                .baseUrl(baseUrl)
                .modelName(modelName)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .maxRetries(0)
                .build();
        this.promptTemplate = promptTemplate;
    }

    @Override
    @CircuitBreaker(name = "ollamaChat")
    @Retry(name = "ollamaChat")
    @Bulkhead(name = "ollamaChat")
    public String summarize(String existingSummary, List<ChatMessage> messages, int maxChars) {
        if (messages == null || messages.isEmpty()) {
            return existingSummary == null ? "" : existingSummary.strip();
        }
        String prompt = String.format(promptTemplate,
                existingSummary == null || existingSummary.isBlank() ? "无" : existingSummary.strip(),
                toDialogue(messages),
                maxChars);
        try {
            return truncate(chatModel.chat(prompt), maxChars);
        } catch (Exception e) {
            throw new DomainException("聊天记忆摘要失败", e);
        }
    }

    private String toDialogue(List<ChatMessage> messages) {
        StringBuilder builder = new StringBuilder();
        for (ChatMessage message : messages) {
            builder.append(message.role() == ChatRole.USER ? "用户: " : "助手: ")
                    .append(message.content())
                    .append('\n');
        }
        return builder.toString();
    }

    private String truncate(String text, int maxChars) {
        if (text == null) {
            return "";
        }
        String result = text.strip();
        return result.length() <= maxChars ? result : result.substring(0, maxChars);
    }
}
