package com.synapse.kb.adapter.out.persistence;

import com.synapse.kb.adapter.out.persistence.entity.ChunkPostingDocument;
import com.synapse.kb.model.ChunkReference;
import com.synapse.kb.model.KnowledgeBaseId;
import com.synapse.kb.port.out.RetrievalFilter;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.util.*;

class MongoChunkSearchQuery {

    private final MongoTemplate mongoTemplate;
    private final int maxCandidates;
    private final Bm25Scorer bm25Scorer = new Bm25Scorer();
    private final ChunkIndexDocumentMapper mapper = new ChunkIndexDocumentMapper();

    MongoChunkSearchQuery(MongoTemplate mongoTemplate, int maxCandidates) {
        this.mongoTemplate = mongoTemplate;
        this.maxCandidates = Math.max(100, maxCandidates);
    }

    List<ChunkReference> search(KnowledgeBaseId knowledgeBaseId, List<String> queryTokens, RetrievalFilter filter,
                                ChunkDocumentFrequencyCache dfCache, long totalChunks, double avgTokenCount) {
        if (queryTokens.isEmpty()) {
            return List.of();
        }

        List<String> uniqueQueryTokens = new ArrayList<>(new LinkedHashSet<>(queryTokens));
        long asOfEpochDay = filter.asOfDate().toEpochDay();

        Criteria criteria = Criteria.where("knowledgeBaseId").is(knowledgeBaseId.value())
                .and("token").in(uniqueQueryTokens)
                .and("lifecycleStatus").in("ACTIVE", "SUPERSEDED")
                .and("effectiveFromEpochDay").lte(asOfEpochDay)
                .andOperator(
                        new Criteria().orOperator(
                                Criteria.where("effectiveToEpochDay").is(Long.MAX_VALUE),
                                Criteria.where("effectiveToEpochDay").gt(asOfEpochDay)
                        )
                );

        if (filter.sourceType() != null) {
            criteria.and("sourceType").is(filter.sourceType().name());
        }
        if (filter.jurisdiction() != null && !filter.jurisdiction().isBlank()) {
            criteria.and("jurisdiction").is(filter.jurisdiction());
        }

        List<ChunkPostingDocument> postings = mongoTemplate.find(
                new Query(criteria).limit(maxCandidates),
                ChunkPostingDocument.class
        );
        if (postings.isEmpty()) {
            return List.of();
        }

        Map<String, Long> documentFrequencies = documentFrequencies(knowledgeBaseId, uniqueQueryTokens, dfCache);
        Map<String, PostingCandidate> candidates = mergePostings(postings);

        List<ScoredChunk> scored = candidates.values().stream()
                .map(candidate -> new ScoredChunk(candidate, bm25Scorer.bm25(candidate, uniqueQueryTokens,
                        documentFrequencies, totalChunks, avgTokenCount)))
                .filter(chunk -> chunk.score > 0)
                .sorted(Comparator.comparingDouble(ScoredChunk::score).reversed())
                .limit(maxCandidates)
                .toList();

        double maxScore = scored.stream().mapToDouble(ScoredChunk::score).max().orElse(1.0);
        return scored.stream()
                .map(chunk -> mapper.toReference(chunk.document, bm25Scorer.normalize(chunk.score, maxScore)))
                .toList();
    }

    private Map<String, Long> documentFrequencies(KnowledgeBaseId knowledgeBaseId, List<String> queryTokens,
                                                   ChunkDocumentFrequencyCache dfCache) {
        Map<String, Long> frequencies = new HashMap<>();
        for (String token : queryTokens) {
            long count = dfCache.cachedDocumentFrequency(knowledgeBaseId, token);
            frequencies.put(token, Math.max(1, count));
        }
        return frequencies;
    }

    private Map<String, PostingCandidate> mergePostings(List<ChunkPostingDocument> postings) {
        Map<String, PostingCandidate> candidates = new HashMap<>();
        for (ChunkPostingDocument posting : postings) {
            String key = posting.getDocumentId() + ":" + posting.getChunkIndex();
            PostingCandidate candidate = candidates.computeIfAbsent(key, ignored -> new PostingCandidate(posting));
            candidate.termFrequencies.put(posting.getToken(), posting.getTf());
        }
        return candidates;
    }

    private record ScoredChunk(PostingCandidate document, double score) {
    }
}
