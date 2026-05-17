package com.synapse.kb.adapter.out.persistence;

import com.synapse.kb.adapter.out.persistence.entity.ChunkIndexDocument;
import com.synapse.kb.model.ChunkReference;
import com.synapse.kb.model.DocumentChunk;
import com.synapse.kb.model.DocumentId;
import com.synapse.kb.model.KnowledgeBaseId;
import com.synapse.kb.port.out.ChunkSearchIndexPort;
import com.synapse.shared.exception.DomainException;
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

    public MongoChunkSearchIndexAdapter(ChunkIndexMongoRepository repository) {
        this.repository = repository;
    }

    @Override
    public void store(KnowledgeBaseId knowledgeBaseId, DocumentId documentId, String documentName, List<DocumentChunk> chunks) {
        try {
            repository.saveAll(chunks.stream()
                    .map(chunk -> toDocument(knowledgeBaseId, documentId, documentName, chunk))
                    .toList());
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
            List<ChunkIndexDocument> candidates = repository.findByKnowledgeBaseIdAndTokensIn(
                    knowledgeBaseId.value(), uniqueQueryTokens
            );
            if (candidates.isEmpty()) {
                return List.of();
            }

            long totalChunks = Math.max(1, repository.countByKnowledgeBaseId(knowledgeBaseId.value()));
            double avgTokenCount = candidates.stream()
                    .mapToInt(ChunkIndexDocument::getTokenCount)
                    .average()
                    .orElse(1.0);
            Map<String, Long> documentFrequencies = documentFrequencies(candidates, uniqueQueryTokens);

            List<ScoredChunk> scored = candidates.stream()
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
            repository.deleteByKnowledgeBaseIdAndDocumentId(knowledgeBaseId.value(), documentId.value());
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

    private Map<String, Long> documentFrequencies(List<ChunkIndexDocument> candidates, List<String> queryTokens) {
        Map<String, Long> frequencies = new HashMap<>();
        for (String token : queryTokens) {
            long count = candidates.stream()
                    .filter(candidate -> candidate.getTermFrequencies().containsKey(token))
                    .count();
            frequencies.put(token, Math.max(1, count));
        }
        return frequencies;
    }

    private double bm25(ChunkIndexDocument document, List<String> queryTokens,
                        Map<String, Long> documentFrequencies, long totalChunks, double avgTokenCount) {
        double score = 0;
        int documentLength = Math.max(1, document.getTokenCount());
        for (String token : queryTokens) {
            int tf = document.getTermFrequencies().getOrDefault(token, 0);
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

    private ChunkReference toReference(ChunkIndexDocument document, float score) {
        return new ChunkReference(
                document.getDocumentId(),
                document.getDocumentName(),
                document.getChunkIndex(),
                document.getChunkText(),
                score,
                document.getStartPosition(),
                document.getEndPosition()
        );
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

    private record ScoredChunk(ChunkIndexDocument document, double score) {
    }
}
