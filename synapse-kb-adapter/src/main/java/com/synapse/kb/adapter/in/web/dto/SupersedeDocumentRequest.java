package com.synapse.kb.adapter.in.web.dto;

import java.time.LocalDate;

public record SupersedeDocumentRequest(
        String newDocumentId,
        LocalDate effectiveTo
) {
}
