package com.synapse.kb.adapter.out.vector;

import com.synapse.kb.model.KnowledgeBaseId;
import com.synapse.kb.port.out.RetrievalFilter;

import java.time.LocalDate;

class MilvusSearchFilterBuilder {
    private final MilvusFilterValueEscaper escaper = new MilvusFilterValueEscaper();

    String buildSearchFilter(KnowledgeBaseId knowledgeBaseId, RetrievalFilter filter) {
        long asOfEpochDay = filter.asOfDate().toEpochDay();
        StringBuilder sb = new StringBuilder();
        sb.append("knowledgeBaseId == '").append(escaper.escape(knowledgeBaseId.value())).append("'");
        sb.append(" && effectiveFromEpochDay <= ").append(asOfEpochDay);
        sb.append(" && (effectiveToEpochDay == ").append(Long.MAX_VALUE).append(" || effectiveToEpochDay > ").append(asOfEpochDay).append(")");
        sb.append(" && lifecycleStatus in ['ACTIVE', 'SUPERSEDED']");
        if (filter.sourceType() != null) {
            sb.append(" && sourceType == '").append(escaper.escape(filter.sourceType().name())).append("'");
        }
        if (filter.jurisdiction() != null && !filter.jurisdiction().isBlank()) {
            sb.append(" && jurisdiction == '").append(escaper.escape(filter.jurisdiction())).append("'");
        }
        return sb.toString();
    }
}
