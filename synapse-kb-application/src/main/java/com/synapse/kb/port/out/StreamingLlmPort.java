package com.synapse.kb.port.out;

import java.util.stream.Stream;

/**
 * 流式大语言模型生成端口（出站端口 / SPI）。
 *
 * <p>将组装好的 prompt 送给 LLM，以流式方式返回生成的文本片段。
 * 返回 {@link Stream}{@code <String>} 以保持 application 层对框架无依赖。
 * 由适配器层（如 {@code OllamaStreamingLlmAdapter}）实现，
 * 并在适配器层将 {@code Stream<String>} 包装为 Reactor {@code Flux<String>}。
 */
public interface StreamingLlmPort {

    /**
     * 流式生成文本。
     *
     * @param prompt 组装好的提示词（已注入检索上下文）
     * @return LLM 生成的文本片段流，每个元素通常为一个 token 或一批 token
     */
    Stream<String> generateStream(String prompt);
}
