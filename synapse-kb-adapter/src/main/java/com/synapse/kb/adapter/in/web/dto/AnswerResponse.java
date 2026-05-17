package com.synapse.kb.adapter.in.web.dto;

import java.util.List;

public record AnswerResponse(String answer, List<ChunkReferenceResponse> references) {
}
