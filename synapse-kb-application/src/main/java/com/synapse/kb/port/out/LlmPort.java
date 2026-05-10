package com.synapse.kb.port.out;

/**
 * 大语言模型生成端口（出站端口 / SPI）。
 *
 * <p>将组装好的 prompt 送给 LLM，返回生成的文本。
 * 由适配器层（如 {@code OllamaLlmAdapter}）实现。
 *
 * <p>流式输出不走此端口，由专门的流式服务处理。
 */
public interface LlmPort {

    /**
     * 同步生成文本。
     *
     * @param prompt 组装好的提示词（已注入检索上下文）
     * @return LLM 生成的完整回答文本
     */
    String generate(String prompt);
}
