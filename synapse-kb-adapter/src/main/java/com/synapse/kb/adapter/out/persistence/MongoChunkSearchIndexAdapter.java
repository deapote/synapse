package com.synapse.kb.adapter.out.persistence;

import com.synapse.kb.adapter.out.persistence.entity.ChunkCorpusStatsDocument;
import com.synapse.kb.adapter.out.persistence.entity.ChunkIndexDocument;
import com.synapse.kb.adapter.out.persistence.entity.ChunkPostingDocument;
import com.synapse.kb.model.ChunkReference;
import com.synapse.kb.model.DocumentChunk;
import com.synapse.kb.model.DocumentId;
import com.synapse.kb.model.DocumentLifecycleStatus;
import com.synapse.kb.model.DocumentMetadata;
import com.synapse.kb.model.KnowledgeBaseId;
import com.synapse.kb.port.out.ChunkSearchIndexPort;
import com.synapse.kb.port.out.RetrievalFilter;
import com.synapse.shared.exception.DomainException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** MongoDB 轻量 BM25 分块检索索引。 */
@Component
public class MongoChunkSearchIndexAdapter implements ChunkSearchIndexPort {

    private static final double K1 = 1.5;
    private static final double B = 0.75;

    private final ChunkIndexMongoRepository repository;
    private final MongoTemplate mongoTemplate;
    private final int maxCandidates;
    private final long documentFrequencyCacheTtlMillis;
    private final Map<DocumentFrequencyCacheKey, CachedLong> documentFrequencyCache = new ConcurrentHashMap<>();

    public MongoChunkSearchIndexAdapter(ChunkIndexMongoRepository repository,
                                        MongoTemplate mongoTemplate,
                                        @Value("${synapse.rag.keyword.max-candidates:5000}") int maxCandidates,
                                        @Value("${synapse.rag.keyword.document-frequency-cache-ttl-seconds:300}") long documentFrequencyCacheTtlSeconds) {
        this.repository = repository;
        this.mongoTemplate = mongoTemplate;
        this.maxCandidates = Math.max(100, maxCandidates);
        this.documentFrequencyCacheTtlMillis = Math.max(10, documentFrequencyCacheTtlSeconds) * 1000L;
    }

    @Override
    public void store(KnowledgeBaseId knowledgeBaseId, DocumentId documentId, String documentName,
                      List<DocumentChunk> chunks, DocumentMetadata metadata) {
        try {
            deleteByDocumentId(knowledgeBaseId, documentId);
            List<ChunkIndexDocument> chunkDocuments = chunks.stream()
                    .map(chunk -> toDocument(knowledgeBaseId, documentId, documentName, chunk, metadata))
                    .toList();
            repository.saveAll(chunkDocuments);
            List<ChunkPostingDocument> postings = new ArrayList<>();
            long tokenCount = 0;
            for (ChunkIndexDocument chunk : chunkDocuments) {
                tokenCount += chunk.getTokenCount();
                for (Map.Entry<String, Integer> entry : chunk.getTermFrequencies().entrySet()) {
                    postings.add(toPosting(chunk, entry.getKey(), entry.getValue()));
                }
            }
            if (!postings.isEmpty()) {
                mongoTemplate.insert(postings, ChunkPostingDocument.class);
            }
            mongoTemplate.upsert(
                    new Query(Criteria.where("_id").is(knowledgeBaseId.value())),
                    new Update()
                            .inc("totalChunks", chunkDocuments.size())
                            .inc("totalTokenCount", tokenCount),
                    ChunkCorpusStatsDocument.class
            );
            invalidateDocumentFrequencyCache(knowledgeBaseId);
        } catch (Exception e) {
            throw new DomainException("关键词索引写入失败", e);
        }
    }

    @Override
    public void refreshStore(KnowledgeBaseId knowledgeBaseId, DocumentId documentId, String documentName,
                             List<DocumentChunk> chunks, DocumentMetadata metadata) {
        try {
            // 1. 记录旧数据
            List<ChunkIndexDocument> oldChunks = repository.findByKnowledgeBaseIdAndDocumentId(
                    knowledgeBaseId.value(), documentId.value());
            long oldTotalTokenCount = oldChunks.stream().mapToInt(ChunkIndexDocument::getTokenCount).sum();

            // 2. 识别新 chunks 中不存在的旧 chunk index（chunk 数量减少时）
            java.util.Set<Integer> newChunkIndices = chunks.stream()
                    .map(DocumentChunk::index)
                    .collect(java.util.stream.Collectors.toSet());
            List<ChunkIndexDocument> orphanedOldChunks = oldChunks.stream()
                    .filter(c -> !newChunkIndices.contains(c.getChunkIndex()))
                    .toList();

            // 3. 写入新 chunk index（saveAll 会覆盖同 ID 的旧记录）
            List<ChunkIndexDocument> chunkDocuments = chunks.stream()
                    .map(chunk -> toDocument(knowledgeBaseId, documentId, documentName, chunk, metadata))
                    .toList();
            repository.saveAll(chunkDocuments);

            // 4. 删除不再存在的旧 chunk index
            for (ChunkIndexDocument orphan : orphanedOldChunks) {
                repository.deleteById(orphan.getId());
            }

            // 5. 准备新 postings
            List<ChunkPostingDocument> newPostings = new ArrayList<>();
            long newTotalTokenCount = 0;
            for (ChunkIndexDocument chunk : chunkDocuments) {
                newTotalTokenCount += chunk.getTokenCount();
                for (Map.Entry<String, Integer> entry : chunk.getTermFrequencies().entrySet()) {
                    newPostings.add(toPosting(chunk, entry.getKey(), entry.getValue()));
                }
            }

            // 6. 获取旧 postings 的所有 _id
            List<ChunkPostingDocument> oldPostings = mongoTemplate.find(
                    new Query(Criteria.where("knowledgeBaseId").is(knowledgeBaseId.value())
                            .and("documentId").is(documentId.value())),
                    ChunkPostingDocument.class);
            java.util.Set<String> oldPostingIds = oldPostings.stream()
                    .map(ChunkPostingDocument::getId)
                    .collect(java.util.stream.Collectors.toSet());

            // 7. 保存新 postings（upsert：同 ID 覆盖旧记录，不同 ID 新增）
            for (ChunkPostingDocument posting : newPostings) {
                mongoTemplate.save(posting);
            }

            // 8. 按 _id 差集删除旧 postings（精确到每个 posting，不依赖 token 集合）
            java.util.Set<String> newPostingIds = newPostings.stream()
                    .map(ChunkPostingDocument::getId)
                    .collect(java.util.stream.Collectors.toSet());
            List<String> postingIdsToDelete = oldPostingIds.stream()
                    .filter(id -> !newPostingIds.contains(id))
                    .toList();
            if (!postingIdsToDelete.isEmpty()) {
                mongoTemplate.remove(
                        new Query(Criteria.where("_id").in(postingIdsToDelete)),
                        ChunkPostingDocument.class);
            }

            // 9. 更新 corpus stats：直接用新总量 - 旧总量的 diff
            mongoTemplate.upsert(
                    new Query(Criteria.where("_id").is(knowledgeBaseId.value())),
                    new Update()
                            .inc("totalChunks", chunkDocuments.size() - oldChunks.size())
                            .inc("totalTokenCount", newTotalTokenCount - oldTotalTokenCount),
                    ChunkCorpusStatsDocument.class
            );

            invalidateDocumentFrequencyCache(knowledgeBaseId);
        } catch (Exception e) {
            throw new DomainException("关键词索引刷新失败", e);
        }
    }

    @Override
    public List<ChunkReference> search(KnowledgeBaseId knowledgeBaseId, String query, int topK, RetrievalFilter filter) {
        List<String> queryTokens = tokenize(query);
        if (queryTokens.isEmpty()) {
            return List.of();
        }

        try {
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

            ChunkCorpusStatsDocument stats = mongoTemplate.findById(
                    knowledgeBaseId.value(), ChunkCorpusStatsDocument.class);
            long totalChunks = stats == null ? Math.max(1, repository.countByKnowledgeBaseId(knowledgeBaseId.value()))
                    : Math.max(1, stats.getTotalChunks());
            double avgTokenCount = stats == null || stats.getTotalChunks() <= 0
                    ? 1.0
                    : Math.max(1.0, (double) stats.getTotalTokenCount() / stats.getTotalChunks());
            Map<String, Long> documentFrequencies = documentFrequencies(knowledgeBaseId, uniqueQueryTokens);
            Map<String, PostingCandidate> candidates = mergePostings(postings);

            List<ScoredChunk> scored = candidates.values().stream()
                    .map(candidate -> new ScoredChunk(candidate, bm25(candidate, uniqueQueryTokens,
                            documentFrequencies, totalChunks, avgTokenCount)))
                    .filter(chunk -> chunk.score > 0)
                    .sorted(Comparator.comparingDouble(ScoredChunk::score).reversed())
                    .limit(topK)
                    .toList();

            double maxScore = scored.stream().mapToDouble(ScoredChunk::score).max().orElse(1.0);
            return scored.stream()
                    .map(chunk -> toReference(chunk.document, normalize(chunk.score, maxScore)))
                    .toList();
        } catch (Exception e) {
            throw new DomainException("关键词检索失败", e);
        }
    }

    @Override
    public void deleteByDocumentId(KnowledgeBaseId knowledgeBaseId, DocumentId documentId) {
        try {
            List<ChunkIndexDocument> chunks = repository.findByKnowledgeBaseIdAndDocumentId(
                    knowledgeBaseId.value(), documentId.value());
            long tokenCount = chunks.stream().mapToInt(ChunkIndexDocument::getTokenCount).sum();
            repository.deleteByKnowledgeBaseIdAndDocumentId(knowledgeBaseId.value(), documentId.value());
            mongoTemplate.remove(new Query(Criteria.where("knowledgeBaseId").is(knowledgeBaseId.value())
                    .and("documentId").is(documentId.value())), ChunkPostingDocument.class);
            if (!chunks.isEmpty()) {
                mongoTemplate.updateFirst(
                        new Query(Criteria.where("_id").is(knowledgeBaseId.value())),
                        new Update()
                                .inc("totalChunks", -chunks.size())
                                .inc("totalTokenCount", -tokenCount),
                        ChunkCorpusStatsDocument.class
                );
                invalidateDocumentFrequencyCache(knowledgeBaseId);
            }
        } catch (Exception e) {
            throw new DomainException("关键词索引删除失败", e);
        }
    }

    @Override
    public void updateDocumentMetadata(KnowledgeBaseId knowledgeBaseId, DocumentId documentId, DocumentMetadata metadata) {
        // v2 使用异步索引刷新任务，不在请求线程中直接更新
    }

    private ChunkIndexDocument toDocument(KnowledgeBaseId knowledgeBaseId, DocumentId documentId,
                                          String documentName, DocumentChunk chunk, DocumentMetadata metadata) {
        List<String> tokens = tokenize(chunk.text());
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

    private Map<String, Long> documentFrequencies(KnowledgeBaseId knowledgeBaseId, List<String> queryTokens) {
        Map<String, Long> frequencies = new HashMap<>();
        for (String token : queryTokens) {
            long count = cachedDocumentFrequency(knowledgeBaseId, token);
            frequencies.put(token, Math.max(1, count));
        }
        return frequencies;
    }

    private long cachedDocumentFrequency(KnowledgeBaseId knowledgeBaseId, String token) {
        long now = System.currentTimeMillis();
        DocumentFrequencyCacheKey key = new DocumentFrequencyCacheKey(knowledgeBaseId.value(), token);
        CachedLong cached = documentFrequencyCache.get(key);
        if (cached != null && cached.expiresAtMillis > now) {
            return cached.value;
        }
        long count = mongoTemplate.count(new Query(Criteria.where("knowledgeBaseId").is(knowledgeBaseId.value())
                .and("token").is(token)), ChunkPostingDocument.class);
        documentFrequencyCache.put(key, new CachedLong(count, now + documentFrequencyCacheTtlMillis));
        return count;
    }

    private void invalidateDocumentFrequencyCache(KnowledgeBaseId knowledgeBaseId) {
        String id = knowledgeBaseId.value();
        documentFrequencyCache.keySet().removeIf(key -> key.knowledgeBaseId().equals(id));
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

    private double bm25(PostingCandidate document, List<String> queryTokens,
                        Map<String, Long> documentFrequencies, long totalChunks, double avgTokenCount) {
        double score = 0;
        int documentLength = Math.max(1, document.tokenCount);
        for (String token : queryTokens) {
            int tf = document.termFrequencies.getOrDefault(token, 0);
            if (tf <= 0) {
                continue;
            }
            long df = documentFrequencies.getOrDefault(token, 1L);
            double idf = Math.log(1 + (totalChunks - df + 0.5) / (df + 0.5));
            double denominator = tf + K1 * (1 - B + B * documentLength / Math.max(1.0, avgTokenCount));
            score += idf * (tf * (K1 + 1)) / denominator;
        }
        return score;
    }

    private float normalize(double score, double maxScore) {
        if (maxScore <= 0) {
            return 0;
        }
        return (float) Math.max(0, Math.min(1, score / maxScore));
    }

    private ChunkReference toReference(PostingCandidate document, float score) {
        LocalDate effectiveFrom = document.effectiveFromEpochDay != 0 && document.effectiveFromEpochDay != Long.MAX_VALUE
                ? LocalDate.ofEpochDay(document.effectiveFromEpochDay)
                : null;
        LocalDate effectiveTo = document.effectiveToEpochDay != 0 && document.effectiveToEpochDay != Long.MAX_VALUE
                ? LocalDate.ofEpochDay(document.effectiveToEpochDay)
                : null;
        DocumentLifecycleStatus lifecycleStatus = document.lifecycleStatus != null && !document.lifecycleStatus.isBlank()
                ? DocumentLifecycleStatus.valueOf(document.lifecycleStatus)
                : DocumentLifecycleStatus.ACTIVE;

        return new ChunkReference(
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

    private ChunkPostingDocument toPosting(ChunkIndexDocument chunk, String token, int tf) {
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

    private Map<String, Integer> termFrequencies(List<String> tokens) {
        Map<String, Integer> frequencies = new HashMap<>();
        for (String token : tokens) {
            frequencies.merge(token, 1, Integer::sum);
        }
        return frequencies;
    }

    private List<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        List<String> tokens = new ArrayList<>();
        StringBuilder latin = new StringBuilder();
        StringBuilder cjk = new StringBuilder();

        for (int i = 0; i < text.length(); i++) {
            char c = Character.toLowerCase(text.charAt(i));
            if (isCjk(c)) {
                flushLatin(latin, tokens);
                cjk.append(c);
                continue;
            }
            flushCjk(cjk, tokens);
            if (Character.isLetterOrDigit(c)) {
                latin.append(c);
            } else {
                flushLatin(latin, tokens);
            }
        }

        flushLatin(latin, tokens);
        flushCjk(cjk, tokens);
        return tokens;
    }

    private void flushLatin(StringBuilder buffer, List<String> tokens) {
        if (buffer.length() >= 2) {
            tokens.add(buffer.toString().toLowerCase(Locale.ROOT));
        }
        buffer.setLength(0);
    }

    private void flushCjk(StringBuilder buffer, List<String> tokens) {
        if (buffer.length() == 0) {
            return;
        }
        for (int i = 0; i < buffer.length(); i++) {
            tokens.add(String.valueOf(buffer.charAt(i)));
        }
        for (int i = 0; i < buffer.length() - 1; i++) {
            tokens.add(buffer.substring(i, i + 2));
        }
        buffer.setLength(0);
    }

    private boolean isCjk(char c) {
        Character.UnicodeScript script = Character.UnicodeScript.of(c);
        return script == Character.UnicodeScript.HAN
                || script == Character.UnicodeScript.HIRAGANA
                || script == Character.UnicodeScript.KATAKANA
                || script == Character.UnicodeScript.HANGUL;
    }

    private record ScoredChunk(PostingCandidate document, double score) {
    }

    private record DocumentFrequencyCacheKey(String knowledgeBaseId, String token) {
    }

    private record CachedLong(long value, long expiresAtMillis) {
    }

    private static class PostingCandidate {
        private final String documentId;
        private final String documentName;
        private final int chunkIndex;
        private final String chunkText;
        private final int startPosition;
        private final int endPosition;
        private final int tokenCount;
        private final long effectiveFromEpochDay;
        private final long effectiveToEpochDay;
        private final String lifecycleStatus;
        private final String canonicalKey;
        private final String versionLabel;
        private final int authorityLevel;
        private final String jurisdiction;
        private final String sourceType;
        private final Map<String, Integer> termFrequencies = new HashMap<>();

        private PostingCandidate(ChunkPostingDocument posting) {
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
}
