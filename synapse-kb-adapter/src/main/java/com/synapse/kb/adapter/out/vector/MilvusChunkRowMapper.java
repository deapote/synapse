package com.synapse.kb.adapter.out.vector;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.synapse.kb.model.ChunkReference;
import com.synapse.kb.model.DocumentChunk;
import com.synapse.kb.model.DocumentId;
import com.synapse.kb.model.DocumentLifecycleStatus;
import com.synapse.kb.model.DocumentMetadata;
import com.synapse.kb.model.KnowledgeBaseId;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

class MilvusChunkRowMapper {

    List<JsonObject> toRows(KnowledgeBaseId knowledgeBaseId, DocumentId documentId, String documentName,
                              List<DocumentChunk> chunks, List<float[]> embeddings, DocumentMetadata metadata) {
        long effectiveFromEpochDay = metadata.effectiveFrom() != null
                ? metadata.effectiveFrom().toEpochDay()
                : LocalDate.now().toEpochDay();
        long effectiveToEpochDay = metadata.effectiveTo() != null
                ? metadata.effectiveTo().toEpochDay()
                : Long.MAX_VALUE;
        String lifecycleStatus = DocumentLifecycleStatus.ACTIVE.name();
        String canonicalKey = metadata.canonicalKey() != null ? metadata.canonicalKey() : "";
        String versionLabel = metadata.versionLabel() != null ? metadata.versionLabel() : "";
        int authorityLevel = metadata.authorityLevel() != null ? metadata.authorityLevel() : 0;
        String jurisdiction = metadata.jurisdiction() != null ? metadata.jurisdiction() : "";
        String sourceType = metadata.sourceType() != null ? metadata.sourceType().name() : "";

        List<JsonObject> data = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            DocumentChunk chunk = chunks.get(i);
            JsonObject row = new JsonObject();
            row.addProperty("id", UUID.randomUUID().toString());
            row.addProperty("knowledgeBaseId", knowledgeBaseId.value());
            row.addProperty("documentId", documentId.value());
            row.addProperty("documentName", documentName);
            row.addProperty("chunkIndex", chunk.index());
            row.addProperty("chunkText", chunk.text());
            row.addProperty("startPosition", chunk.startPosition());
            row.addProperty("endPosition", chunk.endPosition());
            row.addProperty("effectiveFromEpochDay", effectiveFromEpochDay);
            row.addProperty("effectiveToEpochDay", effectiveToEpochDay);
            row.addProperty("lifecycleStatus", lifecycleStatus);
            row.addProperty("canonicalKey", canonicalKey);
            row.addProperty("versionLabel", versionLabel);
            row.addProperty("authorityLevel", authorityLevel);
            row.addProperty("jurisdiction", jurisdiction);
            row.addProperty("sourceType", sourceType);

            JsonArray vec = new JsonArray();
            for (float f : embeddings.get(i)) {
                vec.add(f);
            }
            row.add("vector", vec);
            data.add(row);
        }
        return data;
    }

    ChunkReference toChunkReference(Map<String, Object> entity, float score) {
        String lifecycleStatusStr = (String) entity.get("lifecycleStatus");
        DocumentLifecycleStatus lifecycleStatus = lifecycleStatusStr != null && !lifecycleStatusStr.isBlank()
                ? DocumentLifecycleStatus.valueOf(lifecycleStatusStr)
                : DocumentLifecycleStatus.ACTIVE;

        Long effectiveFromEpochDay = entity.get("effectiveFromEpochDay") instanceof Number
                ? ((Number) entity.get("effectiveFromEpochDay")).longValue()
                : null;
        Long effectiveToEpochDay = entity.get("effectiveToEpochDay") instanceof Number
                ? ((Number) entity.get("effectiveToEpochDay")).longValue()
                : null;

        LocalDate effectiveFrom = effectiveFromEpochDay != null && effectiveFromEpochDay != Long.MAX_VALUE
                ? LocalDate.ofEpochDay(effectiveFromEpochDay)
                : null;
        LocalDate effectiveTo = effectiveToEpochDay != null && effectiveToEpochDay != Long.MAX_VALUE
                ? LocalDate.ofEpochDay(effectiveToEpochDay)
                : null;

        Integer authorityLevel = entity.get("authorityLevel") instanceof Number
                ? ((Number) entity.get("authorityLevel")).intValue()
                : 0;

        return new ChunkReference(
                (String) entity.get("documentId"),
                (String) entity.get("documentName"),
                ((Number) entity.get("chunkIndex")).intValue(),
                (String) entity.get("chunkText"),
                score,
                ((Number) entity.get("startPosition")).intValue(),
                ((Number) entity.get("endPosition")).intValue(),
                (String) entity.get("canonicalKey"),
                (String) entity.get("versionLabel"),
                effectiveFrom,
                effectiveTo,
                lifecycleStatus,
                authorityLevel,
                (String) entity.get("jurisdiction")
        );
    }

    float normalizeScore(Float score) {
        if (score == null) {
            return 0.0f;
        }
        return (score + 1.0f) / 2.0f;
    }
}
