package com.synapse.kb.adapter.out.persistence;

import com.synapse.kb.adapter.out.persistence.entity.ChunkCorpusStatsDocument;
import com.synapse.kb.adapter.out.persistence.entity.ChunkIndexDocument;
import com.synapse.kb.adapter.out.persistence.entity.ChunkPostingDocument;
import com.synapse.kb.model.ChunkReference;
import com.synapse.kb.model.DocumentChunk;
import com.synapse.kb.model.DocumentId;
import com.synapse.kb.model.KnowledgeBaseId;
import com.synapse.kb.port.out.ChunkSearchIndexPort;
import com.synapse.shared.exception.DomainException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** MongoDB 轻量 BM25 分块检索索引。 */
@Component
public class MongoChunkSearchIndexAdapter implements ChunkSearchIndexPort {

    private static final double K1 = 1.5;
    private static final double B = 0.75;

    private final ChunkIndexMongoRepository repository;
    private final MongoTemplate mongoTemplate;
    private final int maxCandidates;

    public MongoChunkSearchIndexAdapter(ChunkIndexMongoRepository repository,
                                        MongoTemplate mongoTemplate,
                                        @Value("${synapse.rag.keyword.max-candidates:5000}") int maxCandidates) {
        this.repository = repository;
        this.mongoTemplate = mongoTemplate;
        this.maxCandidates = Math.max(100, maxCandidates);
    }

    @Override
    public void store(KnowledgeBaseId knowledgeBaseId, DocumentId documentId, String documentName, List<DocumentChunk> chunks) {
        try {
            deleteByDocumentId(knowledgeBaseId, documentId);
            List<ChunkIndexDocument> chunkDocuments = chunks.stream()
                    .map(chunk -> toDocument(knowledgeBaseId, documentId, documentName, chunk))
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
        } catch (Exception e) {
            throw new DomainException("关键词索引写入失败", e);
        }
    }

    @Override
    public List<ChunkReference> search(KnowledgeBaseId knowledgeBaseId, String query, int topK) {
        List<String> queryTokens = tokenize(query);
        if (queryTokens.isEmpty()) {
            return List.of();
        }

        try {
            List<String> uniqueQueryTokens = new ArrayList<>(new LinkedHashSet<>(queryTokens));
            List<ChunkPostingDocument> postings = mongoTemplate.find(
                    new Query(Criteria.where("knowledgeBaseId").is(knowledgeBaseId.value())
                            .and("token").in(uniqueQueryTokens)).limit(maxCandidates),
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
            }
        } catch (Exception e) {
            throw new DomainException("关键词索引删除失败", e);
        }
    }

    private ChunkIndexDocument toDocument(KnowledgeBaseId knowledgeBaseId, DocumentId documentId,
                                          String documentName, DocumentChunk chunk) {
        List<String> tokens = tokenize(chunk.text());
        Map<String, Integer> frequencies = termFrequencies(tokens);

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
        return document;
    }

    private Map<String, Long> documentFrequencies(KnowledgeBaseId knowledgeBaseId, List<String> queryTokens) {
        Map<String, Long> frequencies = new HashMap<>();
        for (String token : queryTokens) {
            long count = mongoTemplate.count(new Query(Criteria.where("knowledgeBaseId").is(knowledgeBaseId.value())
                    .and("token").is(token)), ChunkPostingDocument.class);
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
        return new ChunkReference(
                document.documentId,
                document.documentName,
                document.chunkIndex,
                document.chunkText,
                score,
                document.startPosition,
                document.endPosition
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

    private static class PostingCandidate {
        private final String documentId;
        private final String documentName;
        private final int chunkIndex;
        private final String chunkText;
        private final int startPosition;
        private final int endPosition;
        private final int tokenCount;
        private final Map<String, Integer> termFrequencies = new HashMap<>();

        private PostingCandidate(ChunkPostingDocument posting) {
            this.documentId = posting.getDocumentId();
            this.documentName = posting.getDocumentName();
            this.chunkIndex = posting.getChunkIndex();
            this.chunkText = posting.getChunkText();
            this.startPosition = posting.getStartPosition();
            this.endPosition = posting.getEndPosition();
            this.tokenCount = posting.getTokenCount();
        }
    }
}
