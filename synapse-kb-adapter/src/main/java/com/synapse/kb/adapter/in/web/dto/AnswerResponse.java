package com.synapse.kb.adapter.in.web.dto;

import java.util.List;

/**
 * 问答响应 DTO。
 *
 * @param answer    LLM 生成的回答文本
 * @param references 引用来源列表，按相似度降序排列
 */
public record AnswerResponse(String answer, List<ChunkReferenceResponse> references) {
}
