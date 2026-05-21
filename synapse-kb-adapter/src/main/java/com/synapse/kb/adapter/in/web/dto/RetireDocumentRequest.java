package com.synapse.kb.adapter.in.web.dto;

import java.time.LocalDate;

/**
 * 文档退役请求 DTO。
 */
public record RetireDocumentRequest(
        LocalDate effectiveTo
) {
}
