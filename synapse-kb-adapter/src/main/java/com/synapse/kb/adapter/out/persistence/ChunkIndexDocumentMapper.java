package com.synapse.kb.adapter.out.persistence;

import com.synapse.kb.adapter.out.persistence.entity.ChunkIndexDocument;
import com.synapse.kb.adapter.out.persistence.entity.ChunkPostingDocument;
import com.synapse.kb.model.DocumentChunk;
import com.synapse.kb.model.DocumentId;
import com.synapse.kb.model.DocumentLifecycleStatus;
import com.synapse.kb.model.DocumentMetadata;
import com.synapse.kb.model.KnowledgeBaseId;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class ChunkIndexDocumentMapper {

    private final ChunkTokenizer tokenizer = new ChunkTokenizer();

    ChunkIndexDocument toDocument(KnowledgeBaseId knowledgeBaseId, DocumentId documentId,
                                  String documentName, DocumentChunk chunk, DocumentMetadata metadata) {
        List<String> tokens = tokenizer.tokenize(chunk.text());
        Map<String, Integer> frequencies = termFrequencies(tokens);

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

        ChunkIndexDocument document = new ChunkIndexDocument();
        document.setId(knowledgeBaseId.value() + ":" + documentId.value() + ":" + chunk.index());
        document.setKnowledgeBaseId(knowledgeBaseId.value());
        document.setDocumentId(documentId.value());
        document.setDocumentName(documentName);
        document.setChunkIndex(chunk.index());
        document.setChunkText(chunk.text());
        document.setStartPosition(chunk.startPosition());
        document.setEndPosition(chunk.endPosition());
        document.setTokens(new ArrayList<>(frequencies.keySet()));
        document.setTermFrequencies(frequencies);
        document.setTokenCount(tokens.size());
        document.setEffectiveFromEpochDay(effectiveFromEpochDay);
        document.setEffectiveToEpochDay(effectiveToEpochDay);
        document.setLifecycleStatus(lifecycleStatus);
        document.setCanonicalKey(canonicalKey);
        document.setVersionLabel(versionLabel);
        document.setAuthorityLevel(authorityLevel);
        document.setJurisdiction(jurisdiction);
        document.setSourceType(sourceType);
        return document;
    }

    ChunkPostingDocument toPosting(ChunkIndexDocument chunk, String token, int tf) {
        ChunkPostingDocument posting = new ChunkPostingDocument();
        posting.setId(chunk.getKnowledgeBaseId() + ":" + token + ":" + chunk.getDocumentId() + ":" + chunk.getChunkIndex());
        posting.setKnowledgeBaseId(chunk.getKnowledgeBaseId());
        posting.setToken(token);
        posting.setDocumentId(chunk.getDocumentId());
        posting.setDocumentName(chunk.getDocumentName());
        posting.setChunkIndex(chunk.getChunkIndex());
        posting.setChunkText(chunk.getChunkText());
        posting.setStartPosition(chunk.getStartPosition());
        posting.setEndPosition(chunk.getEndPosition());
        posting.setTf(tf);
        posting.setTokenCount(chunk.getTokenCount());
        posting.setEffectiveFromEpochDay(chunk.getEffectiveFromEpochDay());
        posting.setEffectiveToEpochDay(chunk.getEffectiveToEpochDay());
        posting.setLifecycleStatus(chunk.getLifecycleStatus());
        posting.setCanonicalKey(chunk.getCanonicalKey());
        posting.setVersionLabel(chunk.getVersionLabel());
        posting.setAuthorityLevel(chunk.getAuthorityLevel());
        posting.setJurisdiction(chunk.getJurisdiction());
        posting.setSourceType(chunk.getSourceType());
        return posting;
    }

    com.synapse.kb.model.ChunkReference toReference(PostingCandidate document, float score) {
        java.time.LocalDate effectiveFrom = document.effectiveFromEpochDay != 0 && document.effectiveFromEpochDay != Long.MAX_VALUE
                ? java.time.LocalDate.ofEpochDay(document.effectiveFromEpochDay)
                : null;
        java.time.LocalDate effectiveTo = document.effectiveToEpochDay != 0 && document.effectiveToEpochDay != Long.MAX_VALUE
                ? java.time.LocalDate.ofEpochDay(document.effectiveToEpochDay)
                : null;
        com.synapse.kb.model.DocumentLifecycleStatus lifecycleStatus = document.lifecycleStatus != null && !document.lifecycleStatus.isBlank()
                ? com.synapse.kb.model.DocumentLifecycleStatus.valueOf(document.lifecycleStatus)
                : com.synapse.kb.model.DocumentLifecycleStatus.ACTIVE;

        return new com.synapse.kb.model.ChunkReference(
                document.documentId,
                document.documentName,
                document.chunkIndex,
                document.chunkText,
                score,
                document.startPosition,
                document.endPosition,
                document.canonicalKey,
                document.versionLabel,
                effectiveFrom,
                effectiveTo,
                lifecycleStatus,
                document.authorityLevel,
                document.jurisdiction
        );
    }

    private Map<String, Integer> termFrequencies(List<String> tokens) {
        Map<String, Integer> frequencies = new java.util.HashMap<>();
        for (String token : tokens) {
            frequencies.merge(token, 1, Integer::sum);
        }
        return frequencies;
    }
}
