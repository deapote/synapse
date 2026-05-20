package com.synapse.kb.adapter.in.web.dto;

import java.time.LocalDate;

public record RetireDocumentRequest(
        LocalDate effectiveTo
) {
}
