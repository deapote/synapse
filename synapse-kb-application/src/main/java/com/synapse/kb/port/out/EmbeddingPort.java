package com.synapse.kb.port.out;

import java.util.List;

/**
 * 文本向量化端口（出站端口 / SPI）。
 *
 * <p>将文本转换为高维向量（Embedding），用于向量检索。
 * 由适配器层（如 {@code OllamaEmbeddingAdapter}）实现。
 */
public interface EmbeddingPort {

    /**
     * 将单条文本转换为向量。
     *
     * @param text 原始文本
     * @return 向量数组
     */
    float[] embed(String text);

    /**
     * 批量将多条文本转换为向量。
     *
     * <p>默认实现为逐条调用 {@link #embed(String)}，
     * 具体实现可覆盖以利用批量 API 提升性能。
     *
     * @param texts 原始文本列表
     * @return 向量数组列表，顺序与输入一致
     */
    default List<float[]> embed(List<String> texts) {
        return texts.stream().map(this::embed).toList();
    }
}
