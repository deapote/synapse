package com.synapse.kb.adapter.in.web.dto;

import java.util.List;

/**
 * 问答响应 DTO，包含生成答案与引用片段列表。
 */
public record AnswerResponse(String answer, List<ChunkReferenceResponse> references) {
}
