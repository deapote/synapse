package com.synapse.kb.adapter.in.web.dto;

import java.time.LocalDate;

/**
 * 文档替代请求 DTO。
 */
public record SupersedeDocumentRequest(
        String newDocumentId,
        LocalDate effectiveTo
) {
}
