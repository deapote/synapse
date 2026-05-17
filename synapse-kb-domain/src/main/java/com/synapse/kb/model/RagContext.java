package com.synapse.kb.model;

import com.synapse.shared.exception.DomainException;

import java.util.List;

/** RAG prompt 及其引用来源。 */
public record RagContext(
        String prompt,
        List<ChunkReference> references,
        String sessionId,
        String ownerUserId
) {

    public RagContext {
        if (prompt == null || prompt.isBlank()) {
            throw new DomainException("提示信息不能为空");
        }
        if (references == null) {
            throw new DomainException("引用内容不能为空");
        }
        references = List.copyOf(references);
        sessionId = sessionId == null || sessionId.isBlank() ? null : sessionId.strip();
        ownerUserId = ownerUserId == null || ownerUserId.isBlank() ? null : ownerUserId.strip();
    }

    public RagContext(String prompt, List<ChunkReference> references) {
        this(prompt, references, null, null);
    }
}
