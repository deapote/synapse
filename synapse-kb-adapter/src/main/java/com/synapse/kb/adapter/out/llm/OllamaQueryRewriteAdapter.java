package com.synapse.kb.adapter.out.llm;

import com.synapse.kb.port.out.QueryRewritePort;
import com.synapse.shared.exception.DomainException;
import dev.langchain4j.model.ollama.OllamaChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Ollama Query 改写适配器。 */
@Component
public class OllamaQueryRewriteAdapter implements QueryRewritePort {

    private final OllamaChatModel chatModel;
    private final String promptTemplate;

    public OllamaQueryRewriteAdapter(
            @Value("${ollama.base-url:http://localhost:11434}") String baseUrl,
            @Value("${ollama.chat-model:qwen2.5:7b}") String modelName,
            @Value("${ollama.chat-timeout-seconds:120}") long timeoutSeconds,
            @Value("${synapse.rag.query-rewrite.prompt-template:请在不改变语义的前提下，将下面的问题改写为更适合知识库检索的一句话。只输出改写后的问题，不要解释。\n\n原问题：%s}") String promptTemplate) {
        this.chatModel = OllamaChatModel.builder()
                .baseUrl(baseUrl)
                .modelName(modelName)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .maxRetries(0)
                .build();
        this.promptTemplate = promptTemplate;
    }

    @Override
    public String rewrite(String query) {
        try {
            return sanitize(chatModel.chat(String.format(promptTemplate, query)));
        } catch (Exception e) {
            throw new DomainException("Query 改写失败", e);
        }
    }

    private String sanitize(String text) {
        if (text == null) {
            return "";
        }
        String result = text.strip();
        if ((result.startsWith("\"") && result.endsWith("\""))
                || (result.startsWith("'") && result.endsWith("'"))
                || (result.startsWith("“") && result.endsWith("”"))) {
            result = result.substring(1, result.length() - 1).strip();
        }
        return result.replaceAll("(?i)^改写后[:：]\\s*", "").strip();
    }
}
