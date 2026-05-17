package com.synapse.kb.adapter.in.web.dto;

public record ChunkReferenceResponse(String documentId, String documentName, String chunkText,
                                     float score, int startPosition, int endPosition) {
}
