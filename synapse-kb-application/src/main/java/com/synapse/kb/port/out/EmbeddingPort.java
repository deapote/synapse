package com.synapse.kb.port.out;

import java.util.List;

/** 文本向量化出站端口。 */
public interface EmbeddingPort {

    float[] embed(String text);

    /**
     * 默认逐条向量化；适配器可覆盖为真正批量调用。
     */
    default List<float[]> embed(List<String> texts) {
        return texts.stream().map(this::embed).toList();
    }
}
