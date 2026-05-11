package com.synapse.kb.adapter.out.llm;

import com.synapse.kb.port.out.LlmPort;
import com.synapse.shared.exception.DomainException;
import dev.langchain4j.model.ollama.OllamaChatModel;
import org.springframework.stereotype.Component;

/**
 * Ollama 大语言模型适配器。
 *
 * <p>实现 {@link LlmPort}，通过本地 Ollama 服务调用 chat 模型，
 * 将组装好的 prompt 转换为生成的文本回答。
 *
 * <p>当前使用模型 {@code qwen2.5:7b}，配置暂时硬编码，后续通过
 * {@code OllamaProperties} 外部化。
 */
@Component
public class OllamaLlmAdapter implements LlmPort {

    private final OllamaChatModel chatModel;

    public OllamaLlmAdapter() {
        this.chatModel = OllamaChatModel.builder()
                .baseUrl("http://localhost:11434")
                .modelName("qwen2.5:7b")
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
