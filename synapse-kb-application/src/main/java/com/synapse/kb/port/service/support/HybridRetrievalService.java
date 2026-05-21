package com.synapse.kb.port.service.support;

import com.synapse.kb.model.*;
import com.synapse.kb.port.out.*;
import com.synapse.kb.repository.DocumentRepository;
import com.synapse.shared.exception.DomainException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

/**
 * 混合检索服务，融合向量召回与 BM25 关键词召回。
 * 并行执行双路检索，加权融合重排后再经文档时效与权威级别过滤、去重。
 */
public class HybridRetrievalService {
    private static final Logger log = LoggerFactory.getLogger(HybridRetrievalService.class);
    private final VectorStorePort vectorStorePort;
    private final ChunkSearchIndexPort chunkSearchIndexPort;
    private final Executor retrievalExecutor;
    private final int topK;
    private final int vectorCandidateK;
    private final int keywordCandidateK;
    private final double vectorWeight;
    private final double keywordWeight;

    public HybridRetrievalService(VectorStorePort vectorStorePort,
                                  ChunkSearchIndexPort chunkSearchIndexPort,
                                  Executor retrievalExecutor,
                                  int topK, int vectorCandidateK, int keywordCandidateK,
                                  double vectorWeight, double keywordWeight) {
        this.vectorStorePort = vectorStorePort;
        this.chunkSearchIndexPort = chunkSearchIndexPort;
        this.retrievalExecutor = retrievalExecutor;
        this.topK = Math.max(1, Math.min(topK, 20));
        this.vectorCandidateK = Math.max(this.topK, Math.min(vectorCandidateK, 100));
        this.keywordCandidateK = Math.max(this.topK, Math.min(keywordCandidateK, 100));
        double safeVectorWeight = Math.max(0, vectorWeight);
        double safeKeywordWeight = Math.max(0, keywordWeight);
        double totalWeight = safeVectorWeight + safeKeywordWeight;
        if (totalWeight == 0) {
            safeVectorWeight = 1;
            totalWeight = 1;
        }
        this.vectorWeight = safeVectorWeight / totalWeight;
        this.keywordWeight = safeKeywordWeight / totalWeight;
    }

    public List<ChunkReference> retrieveReferences(
            KnowledgeBaseId knowledgeBaseId,
            QueryPreparationService.PreparedQuery preparedQuery,
            LocalDate asOfDate,
            DocumentSourceType sourceType,
            String jurisdiction) {
        RetrievalFilter filter = new RetrievalFilter(asOfDate, sourceType, jurisdiction);
        CompletableFuture<List<ChunkReference>> vectorFuture = CompletableFuture.supplyAsync(
                () -> vectorStorePort.search(knowledgeBaseId, preparedQuery.embedding(), vectorCandidateK, filter),
                retrievalExecutor
        );
        CompletableFuture<List<ChunkReference>> keywordFuture = CompletableFuture.supplyAsync(
                () -> chunkSearchIndexPort.search(knowledgeBaseId, preparedQuery.effectiveText(), keywordCandidateK, filter),
                retrievalExecutor
        );
        return mergeAndRerank(await(vectorFuture), await(keywordFuture));
    }

    public List<ChunkReference> filterByDocumentEffectiveDate(
            List<ChunkReference> references,
            LocalDate asOfDate,
            DocumentSourceType sourceType,
            String jurisdiction,
            DocumentRepository documentRepository) {
        if (references.isEmpty()) {
            return references;
        }
        Set<String> documentIds = references.stream()
                .map(ChunkReference::documentId)
                .collect(java.util.stream.Collectors.toSet());
        Map<String, Document> documentMap = new HashMap<>();
        for (String docId : documentIds) {
            documentRepository.findById(new DocumentId(docId))
                    .ifPresent(doc -> documentMap.put(docId, doc));
        }
        return references.stream()
                .filter(ref -> {
                    Document doc = documentMap.get(ref.documentId());
                    if (doc == null) {
                        return true;
                    }
                    if (doc.getIndexStatus() != DocumentIndexStatus.SYNCED) {
                        return false;
                    }
                    if (!doc.isEffectiveOn(asOfDate)) {
                        return false;
                    }
                    if (sourceType != null && sourceType != doc.getSourceType()) {
                        return false;
                    }
                    if (jurisdiction != null && !jurisdiction.equals(doc.getJurisdiction())) {
                        return false;
                    }
                    return true;
                })
                .map(ref -> {
                    Document doc = documentMap.get(ref.documentId());
                    if (doc == null) {
                        return ref;
                    }
                    return new ChunkReference(
                            ref.documentId(), ref.documentName(), ref.chunkIndex(), ref.chunkText(),
                            ref.score(), ref.startPosition(), ref.endPosition(),
                            doc.getCanonicalKey(), doc.getVersionLabel(),
                            doc.getEffectiveFrom(), doc.getEffectiveTo(),
                            doc.getLifecycleStatus(), doc.getAuthorityLevel(), doc.getJurisdiction()
                    );
                })
                .toList();
    }

    public List<ChunkReference> deduplicateByCanonicalKey(List<ChunkReference> ranked) {
        Map<String, ChunkReference> bestByCanonicalKey = new LinkedHashMap<>();
        List<ChunkReference> result = new ArrayList<>();

        for (ChunkReference ref : ranked) {
            String key = ref.canonicalKey();
            if (key == null || key.isBlank()) {
                result.add(ref);
                continue;
            }
            ChunkReference existing = bestByCanonicalKey.get(key);
            if (existing == null) {
                bestByCanonicalKey.put(key, ref);
                result.add(ref);
            } else {
                if (ref.authorityLevel() > existing.authorityLevel()
                        || (ref.authorityLevel() == existing.authorityLevel()
                        && ref.effectiveFrom() != null
                        && existing.effectiveFrom() != null
                        && ref.effectiveFrom().isAfter(existing.effectiveFrom()))) {
                    bestByCanonicalKey.put(key, ref);
                    int idx = result.indexOf(existing);
                    if (idx >= 0) {
                        result.set(idx, ref);
                    }
                }
            }
        }
        return result.stream().limit(topK).toList();
    }

    private List<ChunkReference> mergeAndRerank(List<ChunkReference> vectorResults, List<ChunkReference> keywordResults) {
        Map<String, RankedReference> merged = new LinkedHashMap<>();
        for (ChunkReference reference : vectorResults) {
            merged.computeIfAbsent(referenceKey(reference), key -> new RankedReference(reference))
                    .vectorScore = reference.score();
        }
        for (ChunkReference reference : keywordResults) {
            merged.computeIfAbsent(referenceKey(reference), key -> new RankedReference(reference))
                    .keywordScore = reference.score();
        }

        return merged.values().stream()
                .map(this::toFinalReference)
                .sorted(Comparator.comparing(ChunkReference::score).reversed())
                .toList();
    }

    private ChunkReference toFinalReference(RankedReference ranked) {
        ChunkReference ref = ranked.reference;
        float score = (float) Math.min(1.0, vectorWeight * ranked.vectorScore + keywordWeight * ranked.keywordScore);
        return new ChunkReference(
                ref.documentId(),
                ref.documentName(),
                ref.chunkIndex(),
                ref.chunkText(),
                score,
                ref.startPosition(),
                ref.endPosition(),
                ref.canonicalKey(),
                ref.versionLabel(),
                ref.effectiveFrom(),
                ref.effectiveTo(),
                ref.lifecycleStatus(),
                ref.authorityLevel(),
                ref.jurisdiction()
        );
    }

    private String referenceKey(ChunkReference reference) {
        return reference.documentId() + ":" + reference.chunkIndex();
    }

    private List<ChunkReference> await(CompletableFuture<List<ChunkReference>> future) {
        try {
            return future.join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new DomainException("检索失败", e);
        }
    }

    private static class RankedReference {
        private final ChunkReference reference;
        private float vectorScore;
        private float keywordScore;

        private RankedReference(ChunkReference reference) {
            this.reference = reference;
        }
    }
}
