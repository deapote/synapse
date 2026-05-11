package com.synapse.kb.adapter.out.llm;

import com.synapse.kb.port.out.LlmPort;
import com.synapse.shared.exception.DomainException;
import dev.langchain4j.model.ollama.OllamaChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Ollama 大语言模型适配器。
 *
 * <p>实现 {@link LlmPort}，通过本地 Ollama 服务调用 chat 模型，
 * 将组装好的 prompt 转换为生成的文本回答。
 *
 * <p>配置从 {@code application.yml} 读取：
 * <ul>
 *   <li>{@code ollama.base-url} — Ollama 服务地址</li>
 *   <li>{@code ollama.chat-model} — 对话模型名称</li>
 * </ul>
 */
@Component
public class OllamaLlmAdapter implements LlmPort {

    private final OllamaChatModel chatModel;

    public OllamaLlmAdapter(
            @Value("${ollama.base-url:http://localhost:11434}") String baseUrl,
            @Value("${ollama.chat-model:qwen2.5:7b}") String modelName) {
        this.chatModel = OllamaChatModel.builder()
                .baseUrl(baseUrl)
                .modelName(modelName)
                .build();
    }

    /**
     * 同步生成文本回答。
     *
     * @param prompt 组装好的提示词（已注入检索上下文）
     * @return LLM 生成的完整回答文本
     * @throws DomainException Ollama 服务不可用或模型调用失败时抛出
     */
    @Override
    public String generate(String prompt) {
        try {
            return chatModel.chat(prompt);
        } catch (Exception e) {
            throw new DomainException("LLM 生成失败", e);
        }
    }
}
