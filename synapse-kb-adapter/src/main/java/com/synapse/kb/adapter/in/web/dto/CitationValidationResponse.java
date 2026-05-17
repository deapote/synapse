package com.synapse.kb.adapter.in.web.dto;

import java.util.List;

public record CitationValidationResponse(boolean trusted,
                                         List<Integer> usedSourceIds,
                                         List<String> warnings) {
}
