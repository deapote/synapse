package com.synapse.kb.model;

import com.synapse.shared.exception.DomainException;

import java.util.List;

/** 非流式回答值对象。 */
public record Answer(
        String text,
        List<ChunkReference> references
) {

    public Answer {
        if (text == null || text.isBlank()) {
            throw new DomainException("回答文本不能为空");
        }
        if (references == null) {
            throw new DomainException("引用内容不能为空");
        }
    }
}
