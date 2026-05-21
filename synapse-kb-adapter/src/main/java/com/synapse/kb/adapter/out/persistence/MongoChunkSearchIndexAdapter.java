package com.synapse.kb.adapter.out.persistence;

import com.synapse.kb.adapter.out.persistence.entity.ChunkCorpusStatsDocument;
import com.synapse.kb.model.ChunkReference;
import com.synapse.kb.model.DocumentChunk;
import com.synapse.kb.model.DocumentId;
import com.synapse.kb.model.DocumentMetadata;
import com.synapse.kb.model.KnowledgeBaseId;
import com.synapse.kb.port.out.ChunkSearchIndexPort;
import com.synapse.kb.port.out.RetrievalFilter;
import com.synapse.shared.exception.DomainException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * MongoDB 轻量 BM25 分块检索索引适配器。
 *
 * <p>每个 chunk 拆分为 posting 反排表，支持按 effective 日期、lifecycleStatus、
 * sourceType、jurisdiction 做硬过滤。BM25 参数固定为 K1=1.5, B=0.75。</p>
 *
 * <p>安全刷新策略：refreshStore 先写入新 postings，再按 _id 差集删除旧 postings。
 * 不采用 delete-first，避免刷新失败时 keywords 索引完全丢失导致检索空窗。
 * 旧 postings 在刷新过程中仍可供检索，虽然可能返回部分过期 metadata，
 * 但最终会在 application 层的 filterByDocumentEffectiveDate 中被 Mongo 权威状态兜底过滤。</p>
 */
@Component
public class MongoChunkSearchIndexAdapter implements ChunkSearchIndexPort {

    private final ChunkIndexMongoRepository repository;
    private final MongoTemplate mongoTemplate;
    private final MongoChunkIndexWriter indexWriter;
    private final MongoChunkSearchQuery searchQuery;
    private final ChunkDocumentFrequencyCache dfCache;
    private final ChunkTokenizer tokenizer = new ChunkTokenizer();

    public MongoChunkSearchIndexAdapter(ChunkIndexMongoRepository repository,
                                        MongoTemplate mongoTemplate,
                                        @Value("${synapse.rag.keyword.max-candidates:5000}") int maxCandidates,
                                        @Value("${synapse.rag.keyword.document-frequency-cache-ttl-seconds:300}") long documentFrequencyCacheTtlSeconds) {
        this.repository = repository;
        this.mongoTemplate = mongoTemplate;
        this.indexWriter = new MongoChunkIndexWriter(repository, mongoTemplate);
        this.searchQuery = new MongoChunkSearchQuery(mongoTemplate, maxCandidates);
        this.dfCache = new ChunkDocumentFrequencyCache(mongoTemplate, Math.max(10, documentFrequencyCacheTtlSeconds) * 1000L);
    }

    @Override
    public void store(KnowledgeBaseId knowledgeBaseId, DocumentId documentId, String documentName,
                      List<DocumentChunk> chunks, DocumentMetadata metadata) {
        indexWriter.store(knowledgeBaseId, documentId, documentName, chunks, metadata);
        dfCache.invalidate(knowledgeBaseId);
    }

    @Override
    public void refreshStore(KnowledgeBaseId knowledgeBaseId, DocumentId documentId, String documentName,
                             List<DocumentChunk> chunks, DocumentMetadata metadata) {
        indexWriter.refreshStore(knowledgeBaseId, documentId, documentName, chunks, metadata);
        dfCache.invalidate(knowledgeBaseId);
    }

    @Override
    public List<ChunkReference> search(KnowledgeBaseId knowledgeBaseId, String query, int topK, RetrievalFilter filter) {
        List<String> queryTokens = tokenizer.tokenize(query);
        if (queryTokens.isEmpty()) {
            return List.of();
        }
        try {
            ChunkCorpusStatsDocument stats = mongoTemplate.findById(
                    knowledgeBaseId.value(), ChunkCorpusStatsDocument.class);
            long totalChunks = stats == null ? Math.max(1, repository.countByKnowledgeBaseId(knowledgeBaseId.value()))
                    : Math.max(1, stats.getTotalChunks());
            double avgTokenCount = stats == null || stats.getTotalChunks() <= 0
                    ? 1.0
                    : Math.max(1.0, (double) stats.getTotalTokenCount() / stats.getTotalChunks());
            return searchQuery.search(knowledgeBaseId, queryTokens, filter, dfCache, totalChunks, avgTokenCount);
        } catch (Exception e) {
            throw new DomainException("关键词检索失败", e);
        }
    }

    @Override
    public void deleteByDocumentId(KnowledgeBaseId knowledgeBaseId, DocumentId documentId) {
        indexWriter.deleteByDocumentId(knowledgeBaseId, documentId);
        dfCache.invalidate(knowledgeBaseId);
    }

    @Override
    public void updateDocumentMetadata(KnowledgeBaseId knowledgeBaseId, DocumentId documentId, DocumentMetadata metadata) {
        // v2 使用异步索引刷新任务，不在请求线程中直接更新
    }
}
