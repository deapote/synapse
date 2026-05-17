package com.synapse.kb.model;

import com.synapse.shared.exception.DomainException;

/** 文档分块及其原文位置。 */
public record DocumentChunk(
        int index,
        String text,
        int startPosition,
        int endPosition
) {

    public DocumentChunk {
        if (text == null || text.isBlank()) {
            throw new DomainException("文本块不能为空");
        }
        if (startPosition < 0 || endPosition < startPosition) {
            throw new DomainException("无效的位置范围");
        }
    }
}
