package com.synapse.kb.adapter.out.persistence;

import com.synapse.kb.adapter.out.persistence.entity.ChunkPostingDocument;

import java.util.HashMap;
import java.util.Map;

class PostingCandidate {
    final String documentId;
    final String documentName;
    final int chunkIndex;
    final String chunkText;
    final int startPosition;
    final int endPosition;
    final int tokenCount;
    final long effectiveFromEpochDay;
    final long effectiveToEpochDay;
    final String lifecycleStatus;
    final String canonicalKey;
    final String versionLabel;
    final int authorityLevel;
    final String jurisdiction;
    final String sourceType;
    final Map<String, Integer> termFrequencies = new HashMap<>();

    PostingCandidate(ChunkPostingDocument posting) {
        this.documentId = posting.getDocumentId();
        this.documentName = posting.getDocumentName();
        this.chunkIndex = posting.getChunkIndex();
        this.chunkText = posting.getChunkText();
        this.startPosition = posting.getStartPosition();
        this.endPosition = posting.getEndPosition();
        this.tokenCount = posting.getTokenCount();
        this.effectiveFromEpochDay = posting.getEffectiveFromEpochDay();
        this.effectiveToEpochDay = posting.getEffectiveToEpochDay();
        this.lifecycleStatus = posting.getLifecycleStatus();
        this.canonicalKey = posting.getCanonicalKey();
        this.versionLabel = posting.getVersionLabel();
        this.authorityLevel = posting.getAuthorityLevel();
        this.jurisdiction = posting.getJurisdiction();
        this.sourceType = posting.getSourceType();
    }
}
