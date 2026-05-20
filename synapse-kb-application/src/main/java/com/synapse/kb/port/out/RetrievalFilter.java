package com.synapse.kb.port.out;

import com.synapse.kb.model.DocumentSourceType;

import java.time.LocalDate;

/**
 * 检索硬过滤条件，用于 Milvus scalar filter 与 Mongo BM25 查询。
 * {@code asOfDate} 必填，表示查询时点；{@code sourceType} 和 {@code jurisdiction}
 * 只来自用户显式选择，不做 LLM 自动推断。jurisdiction v1 为精确匹配。
 */
public record RetrievalFilter(
        LocalDate asOfDate,
        DocumentSourceType sourceType,
        String jurisdiction
) {
    public RetrievalFilter {
        if (asOfDate == null) {
            throw new IllegalArgumentException("asOfDate 不能为空");
        }
    }
}
