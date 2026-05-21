package com.synapse.kb.adapter.out.vector;

import com.google.gson.JsonObject;
import com.synapse.kb.model.ChunkReference;
import com.synapse.kb.model.DocumentChunk;
import com.synapse.kb.model.DocumentId;
import com.synapse.kb.model.DocumentMetadata;
import com.synapse.kb.model.KnowledgeBaseId;
import com.synapse.kb.port.out.RetrievalFilter;
import com.synapse.kb.port.out.VectorStorePort;
import com.synapse.shared.exception.DomainException;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.MeterRegistry;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.ConsistencyLevel;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.vector.request.DeleteReq;
import io.milvus.v2.service.vector.request.InsertReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.response.SearchResp;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Milvus v3 collection 向量存储适配器。
 *
 * <p>使用 HNSW 索引（COSINE 度量），collection 按 knowledgeBaseId 分区。
 * 所有检索操作均通过 scalar filter 执行硬过滤：effective 日期范围、lifecycleStatus、
 * sourceType、jurisdiction。v1 不做无补偿 delete + reinsert，metadata 变更通过
 * {@link com.synapse.kb.model.DocumentIndexRefreshJob} 异步刷新；旧行在检索阶段
 * 由 Mongo 权威状态兜底过滤保证正确性。</p>
 */
@Component
public class MilvusVectorStoreAdapter implements VectorStorePort {

    private final String host;
    private final int port;
    private final String v3CollectionName;
    private final long connectTimeoutMs;
    private final long rpcDeadlineMs;
    private final ConsistencyLevel consistencyLevel;
    private final io.micrometer.core.instrument.Timer searchTimer;
    private final int hnswEf;

    private volatile MilvusClientV2 client;
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    private final MilvusSearchFilterBuilder filterBuilder = new MilvusSearchFilterBuilder();
    private final MilvusChunkRowMapper rowMapper = new MilvusChunkRowMapper();

    public MilvusVectorStoreAdapter(
            @Value("${milvus.host:127.0.0.1}") String host,
            @Value("${milvus.port:19530}") int port,
            @Value("${milvus.v3.collection-name:synapse_document_chunks_v3}") String v3CollectionName,
            @Value("${milvus.connect-timeout-ms:5000}") long connectTimeoutMs,
            @Value("${milvus.rpc-deadline-ms:30000}") long rpcDeadlineMs,
            @Value("${milvus.v2.hnsw.m:16}") int hnswM,
            @Value("${milvus.v2.hnsw.ef-construction:200}") int hnswEfConstruction,
            @Value("${milvus.v2.hnsw.ef:64}") int hnswEf,
            @Value("${milvus.v2.load-timeout-ms:30000}") long loadTimeoutMs,
            @Value("${milvus.v2.consistency-level:BOUNDED}") String consistencyLevel,
            @Value("${milvus.embedding-dimension:1536}") int vectorDim,
            MeterRegistry meterRegistry) {
        this.host = host;
        this.port = port;
        this.v3CollectionName = v3CollectionName;
        this.connectTimeoutMs = connectTimeoutMs;
        this.rpcDeadlineMs = rpcDeadlineMs;
        this.hnswEf = hnswEf;
        this.consistencyLevel = ConsistencyLevel.valueOf(consistencyLevel.toUpperCase());
        this.searchTimer = io.micrometer.core.instrument.Timer.builder("synapse.milvus.search")
                .description("Milvus vector search latency")
                .register(meterRegistry);
        this.collectionInitializer = new MilvusCollectionInitializer(
                lazyClient(), v3CollectionName, vectorDim, hnswM, hnswEfConstruction, loadTimeoutMs);
    }

    private MilvusCollectionInitializer collectionInitializer;

    private MilvusClientV2 lazyClient() {
        if (client == null) {
            ConnectConfig config = ConnectConfig.builder()
                    .uri("http://" + host + ":" + port)
                    .connectTimeoutMs(connectTimeoutMs)
                    .rpcDeadlineMs(rpcDeadlineMs)
                    .build();
            client = new MilvusClientV2(config);
        }
        return client;
    }

    private void ensureInitialized() {
        if (initialized.get()) {
            return;
        }
        synchronized (this) {
            if (initialized.get()) {
                return;
            }
            collectionInitializer = new MilvusCollectionInitializer(
                    lazyClient(), v3CollectionName, 1536, 16, 200, 30000);
            collectionInitializer.initV3Collection();
            collectionInitializer.loadCollectionIfNeeded(v3CollectionName);
            initialized.set(true);
        }
    }

    @Override
    @CircuitBreaker(name = "milvus")
    @Retry(name = "milvus")
    @Bulkhead(name = "milvus")
    public void store(KnowledgeBaseId knowledgeBaseId, DocumentId documentId, String documentName,
                      List<DocumentChunk> chunks, List<float[]> embeddings, DocumentMetadata metadata) {
        ensureInitialized();
        if (chunks.size() != embeddings.size()) {
            throw new DomainException("分块数量与向量数量不匹配: " + chunks.size() + " vs " + embeddings.size());
        }
        List<JsonObject> data = rowMapper.toRows(knowledgeBaseId, documentId, documentName, chunks, embeddings, metadata);
        try {
            insert(v3CollectionName, data);
        } catch (Exception e) {
            throw new DomainException("向量存储失败", e);
        }
    }

    @Override
    @CircuitBreaker(name = "milvus")
    @Retry(name = "milvus")
    @Bulkhead(name = "milvus")
    public List<ChunkReference> search(KnowledgeBaseId knowledgeBaseId, float[] queryEmbedding, int topK, RetrievalFilter filter) {
        ensureInitialized();
        try {
            return searchTimer.record(() -> searchCollection(v3CollectionName, knowledgeBaseId, queryEmbedding, topK, filter));
        } catch (Exception e) {
            throw new DomainException("向量检索失败", e);
        }
    }

    @Override
    @CircuitBreaker(name = "milvus")
    @Retry(name = "milvus")
    @Bulkhead(name = "milvus")
    public void deleteByDocumentId(KnowledgeBaseId knowledgeBaseId, DocumentId documentId) {
        ensureInitialized();
        boolean deleted = deleteQuietly(v3CollectionName, knowledgeBaseId, documentId);
        if (!deleted) {
            throw new DomainException("向量删除失败");
        }
    }

    @Override
    public void updateDocumentMetadata(KnowledgeBaseId knowledgeBaseId, DocumentId documentId, DocumentMetadata metadata) {
        // v1 使用异步索引刷新任务，不在请求线程中直接更新
    }

    private void insert(String collection, List<JsonObject> data) {
        client.insert(InsertReq.builder()
                .collectionName(collection)
                .data(data)
                .build());
    }

    private void delete(String collection, KnowledgeBaseId knowledgeBaseId, DocumentId documentId) {
        MilvusFilterValueEscaper escaper = new MilvusFilterValueEscaper();
        client.delete(DeleteReq.builder()
                .collectionName(collection)
                .filter("knowledgeBaseId == '" + escaper.escape(knowledgeBaseId.value())
                        + "' && documentId == '" + escaper.escape(documentId.value()) + "'")
                .build());
    }

    private boolean deleteQuietly(String collection, KnowledgeBaseId knowledgeBaseId, DocumentId documentId) {
        try {
            delete(collection, knowledgeBaseId, documentId);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private List<ChunkReference> searchCollection(String collection, KnowledgeBaseId knowledgeBaseId,
                                                  float[] queryEmbedding, int topK, RetrievalFilter filter) {
        String filterExpr = filterBuilder.buildSearchFilter(knowledgeBaseId, filter);
        SearchReq.SearchReqBuilder<?, ?> builder = SearchReq.builder()
                .collectionName(collection)
                .data(List.of(new FloatVec(queryEmbedding)))
                .filter(filterExpr)
                .topK(topK)
                .metricType(IndexParam.MetricType.COSINE)
                .outputFields(List.of("documentId", "documentName", "chunkIndex",
                        "chunkText", "startPosition", "endPosition",
                        "canonicalKey", "versionLabel", "effectiveFromEpochDay", "effectiveToEpochDay",
                        "lifecycleStatus", "authorityLevel", "jurisdiction"));
        builder.searchParams(Map.of("ef", hnswEf)).consistencyLevel(consistencyLevel);
        SearchResp resp = client.search(builder.build());
        List<ChunkReference> results = new ArrayList<>();
        List<List<SearchResp.SearchResult>> batchResults = resp.getSearchResults();
        if (batchResults == null || batchResults.isEmpty()) {
            return results;
        }
        for (SearchResp.SearchResult result : batchResults.get(0)) {
            Map<String, Object> entity = result.getEntity();
            results.add(rowMapper.toChunkReference(entity, rowMapper.normalizeScore(result.getScore())));
        }
        return results;
    }
}
