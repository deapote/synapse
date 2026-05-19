package com.synapse.kb.adapter.in.web.dto;

import java.time.LocalDate;

public record QueryRequest(String query, String sessionId, LocalDate asOfDate) {
}
