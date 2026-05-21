package com.synapse.kb.adapter.in.web.dto;

import java.time.LocalDate;

/**
 * 知识库问答请求 DTO，支持时效与维度过滤。
 */
public record QueryRequest(String query, String sessionId, LocalDate asOfDate, String sourceType, String jurisdiction) {
}
