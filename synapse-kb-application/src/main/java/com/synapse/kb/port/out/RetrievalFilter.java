package com.synapse.kb.port.out;

import com.synapse.kb.model.DocumentSourceType;

import java.time.LocalDate;

/**
 * 检索过滤条件。asOfDate 为必填硬过滤；sourceType 和 jurisdiction 为可选软过滤。
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
