package com.synapse.kb.adapter.in.web.dto;

import java.util.List;

/**
 * 引用校验响应 DTO。
 */
public record CitationValidationResponse(boolean trusted,
                                         List<Integer> usedSourceIds,
                                         List<String> warnings) {
}
