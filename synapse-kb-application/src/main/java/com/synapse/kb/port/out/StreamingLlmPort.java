package com.synapse.kb.port.out;

import java.util.stream.Stream;

/**
 * 流式 LLM 出站端口。返回 JDK Stream，避免 application 层依赖 Reactor。
 */
public interface StreamingLlmPort {

    Stream<String> generateStream(String prompt);
}
