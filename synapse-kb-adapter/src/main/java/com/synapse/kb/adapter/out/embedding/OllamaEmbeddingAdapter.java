package com.synapse.kb.adapter.out.embedding;

import com.synapse.kb.port.out.EmbeddingPort;
import com.synapse.shared.exception.DomainException;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import org.springframework.stereotype.Component;

/**
 * Ollama 文本向量化适配器。
 *
 * <p>实现 {@link EmbeddingPort}，通过本地 Ollama 服务调用 Embedding 模型，
 * 将文本转换为高维浮点向量（当前模型输出 1536 维）。
 *
 * <p>配置暂时硬编码，后续在 {@code KnowledgeBaseBeanConfig} 中通过
 * {@code OllamaProperties} 外部化。
 */
@Component
public class OllamaEmbeddingAdapter implements EmbeddingPort {

    private final OllamaEmbeddingModel embeddingModel;

    public OllamaEmbeddingAdapter() {
        this.embeddingModel = OllamaEmbeddingModel.builder()
                .baseUrl("http://localhost:11434")
                .modelName("hf.co/sinequa/gme-Qwen2-VL-2B-Instruct-GGUF:Q8_0")
                .build();
    }

    /**
     * 将单条文本转换为向量。
     *
     * @param text 原始文本
     * @return 向量数组（维度由模型决定，当前为 1536 维）
     * @throws DomainException Ollama 服务不可用或模型调用失败时抛出
     */
    @Override
    public float[] embed(String text) {
        try {
            return embeddingModel.embed(text).content().vector();
        } catch (Exception e) {
            throw new DomainException("文本向量化失败", e);
        }
    }
}
