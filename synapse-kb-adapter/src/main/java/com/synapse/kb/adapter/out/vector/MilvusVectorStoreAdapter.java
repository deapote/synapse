package com.synapse.kb.adapter.out.vector;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.synapse.kb.model.ChunkReference;
import com.synapse.kb.model.DocumentChunk;
import com.synapse.kb.model.DocumentId;
import com.synapse.kb.model.KnowledgeBaseId;
import com.synapse.kb.port.out.VectorStorePort;
import com.synapse.shared.exception.DomainException;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.MeterRegistry;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.ConsistencyLevel;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.AddFieldReq;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.GetLoadStateReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.collection.request.LoadCollectionReq;
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
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class MilvusVectorStoreAdapter implements VectorStorePort {

    private final String host;
    private final int port;
    private final String legacyCollectionName;
    private final String v2CollectionName;
    private final boolean v2Enabled;
    private final boolean dualWrite;
    private final boolean readV2;
    private final int vectorDim;
    private final long connectTimeoutMs;
    private final long rpcDeadlineMs;
    private final int hnswM;
    private final int hnswEfConstruction;
    private final int hnswEf;
    private final long loadTimeoutMs;
    private final ConsistencyLevel consistencyLevel;
    private final io.micrometer.core.instrument.Timer searchTimer;

    private MilvusClientV2 client;
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    public MilvusVectorStoreAdapter(
            @Value("${milvus.host:127.0.0.1}") String host,
            @Value("${milvus.port:19530}") int port,
            @Value("${milvus.collection-name:synapse_document_chunks}") String legacyCollectionName,
            @Value("${milvus.v2.collection-name:synapse_document_chunks_v2}") String v2CollectionName,
            @Value("${milvus.v2.enabled:true}") boolean v2Enabled,
            @Value("${milvus.v2.dual-write:true}") boolean dualWrite,
            @Value("${milvus.v2.read-enabled:true}") boolean readV2,
            @Value("${milvus.embedding-dimension:1536}") int vectorDim,
            @Value("${milvus.connect-timeout-ms:5000}") long connectTimeoutMs,
            @Value("${milvus.rpc-deadline-ms:30000}") long rpcDeadlineMs,
            @Value("${milvus.v2.hnsw.m:16}") int hnswM,
            @Value("${milvus.v2.hnsw.ef-construction:200}") int hnswEfConstruction,
            @Value("${milvus.v2.hnsw.ef:64}") int hnswEf,
            @Value("${milvus.v2.load-timeout-ms:30000}") long loadTimeoutMs,
            @Value("${milvus.v2.consistency-level:BOUNDED}") String consistencyLevel,
            MeterRegistry meterRegistry) {
        this.host = host;
        this.port = port;
        this.legacyCollectionName = legacyCollectionName;
        this.v2CollectionName = v2CollectionName;
        this.v2Enabled = v2Enabled;
        this.dualWrite = dualWrite;
        this.readV2 = readV2;
        this.vectorDim = vectorDim;
        this.connectTimeoutMs = connectTimeoutMs;
        this.rpcDeadlineMs = rpcDeadlineMs;
        this.hnswM = hnswM;
        this.hnswEfConstruction = hnswEfConstruction;
        this.hnswEf = hnswEf;
        this.loadTimeoutMs = loadTimeoutMs;
        this.consistencyLevel = ConsistencyLevel.valueOf(consistencyLevel.toUpperCase());
        this.searchTimer = io.micrometer.core.instrument.Timer.builder("synapse.milvus.search")
                .description("Milvus vector search latency")
                .register(meterRegistry);
    }

    private void ensureInitialized() {
        if (initialized.get()) {
            return;
        }
        synchronized (this) {
            if (initialized.get()) {
                return;
            }
            ConnectConfig config = ConnectConfig.builder()
                    .uri("http://" + host + ":" + port)
                    .connectTimeoutMs(connectTimeoutMs)
                    .rpcDeadlineMs(rpcDeadlineMs)
                    .build();
            this.client = new MilvusClientV2(config);
        initLegacyCollection();
        loadCollectionIfNeeded(legacyCollectionName);
        if (v2Enabled) {
            initV2Collection();
            loadCollectionIfNeeded(v2CollectionName);
        }
            initialized.set(true);
        }
    }

    private void initLegacyCollection() {
        initCollection(legacyCollectionName, false, IndexParam.IndexType.IVF_FLAT, Map.of("nlist", 128));
    }

    private void initV2Collection() {
        initCollection(v2CollectionName, true, IndexParam.IndexType.HNSW,
                Map.of("M", hnswM, "efConstruction", hnswEfConstruction));
    }

    private void initCollection(String collection, boolean partitionKey, IndexParam.IndexType vectorIndex,
                                Map<String, Object> vectorParams) {
        Boolean exists = client.hasCollection(HasCollectionReq.builder().collectionName(collection).build());
        if (Boolean.TRUE.equals(exists)) {
            return;
        }

        CreateCollectionReq.CollectionSchema schema = client.createSchema();
        schema.addField(AddFieldReq.builder()
                .fieldName("id").dataType(DataType.VarChar)
                .isPrimaryKey(true).maxLength(64).build());
        schema.addField(AddFieldReq.builder()
                .fieldName("knowledgeBaseId").dataType(DataType.VarChar)
                .isPartitionKey(partitionKey).maxLength(64).build());
        schema.addField(AddFieldReq.builder()
                .fieldName("documentId").dataType(DataType.VarChar)
                .maxLength(64).build());
        schema.addField(AddFieldReq.builder()
                .fieldName("documentName").dataType(DataType.VarChar)
                .maxLength(256).build());
        schema.addField(AddFieldReq.builder()
                .fieldName("chunkIndex").dataType(DataType.Int32).build());
        schema.addField(AddFieldReq.builder()
                .fieldName("chunkText").dataType(DataType.VarChar)
                .maxLength(65535).build());
        schema.addField(AddFieldReq.builder()
                .fieldName("vector").dataType(DataType.FloatVector)
                .dimension(vectorDim).build());
        schema.addField(AddFieldReq.builder()
                .fieldName("startPosition").dataType(DataType.Int32).build());
        schema.addField(AddFieldReq.builder()
                .fieldName("endPosition").dataType(DataType.Int32).build());

        List<IndexParam> indexes = new ArrayList<>();
        indexes.add(IndexParam.builder()
                .fieldName("vector")
                .indexType(vectorIndex)
                .metricType(IndexParam.MetricType.COSINE)
                .extraParams(vectorParams)
                .build());
        indexes.add(IndexParam.builder()
                .fieldName("knowledgeBaseId")
                .indexType(IndexParam.IndexType.TRIE)
                .build());
        indexes.add(IndexParam.builder()
                .fieldName("documentId")
                .indexType(IndexParam.IndexType.TRIE)
                .build());

        client.createCollection(CreateCollectionReq.builder()
                .collectionName(collection)
                .collectionSchema(schema)
                .indexParams(indexes)
                .build());
    }

    private void loadCollectionIfNeeded(String collection) {
        Boolean loaded = client.getLoadState(GetLoadStateReq.builder()
                .collectionName(collection)
                .build());
        if (Boolean.TRUE.equals(loaded)) {
            return;
        }
        client.loadCollection(LoadCollectionReq.builder()
                .collectionName(collection)
                .sync(true)
                .timeout(loadTimeoutMs)
                .build());
    }

    @Override
    @CircuitBreaker(name = "milvus")
    @Retry(name = "milvus")
    @Bulkhead(name = "milvus")
    public void store(KnowledgeBaseId knowledgeBaseId, DocumentId documentId, String documentName,
                      List<DocumentChunk> chunks, List<float[]> embeddings) {
        ensureInitialized();
        if (chunks.size() != embeddings.size()) {
            throw new DomainException("分块数量与向量数量不匹配: " + chunks.size() + " vs " + embeddings.size());
        }
        List<JsonObject> data = toRows(knowledgeBaseId, documentId, documentName, chunks, embeddings);
        try {
            insert(legacyCollectionName, data);
            if (v2Enabled && dualWrite) {
                insert(v2CollectionName, data);
            }
        } catch (Exception e) {
            throw new DomainException("向量存储失败", e);
        }
    }

    @Override
    @CircuitBreaker(name = "milvus")
    @Retry(name = "milvus")
    @Bulkhead(name = "milvus")
    public List<ChunkReference> search(KnowledgeBaseId knowledgeBaseId, float[] queryEmbedding, int topK) {
        ensureInitialized();
        try {
            return searchTimer.record(() -> {
                List<ChunkReference> results = v2Enabled && readV2
                        ? searchCollection(v2CollectionName, knowledgeBaseId, queryEmbedding, topK, true)
                        : List.of();
                if (!results.isEmpty()) {
                    return results;
                }
                return searchCollection(legacyCollectionName, knowledgeBaseId, queryEmbedding, topK, false);
            });
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
        boolean legacyDeleted = deleteQuietly(legacyCollectionName, knowledgeBaseId, documentId);
        boolean v2Deleted = !v2Enabled || deleteQuietly(v2CollectionName, knowledgeBaseId, documentId);
        if (!legacyDeleted && !v2Deleted) {
            throw new DomainException("向量删除失败");
        }
    }

    private void insert(String collection, List<JsonObject> data) {
        client.insert(InsertReq.builder()
                .collectionName(collection)
                .data(data)
                .build());
    }

    private void delete(String collection, KnowledgeBaseId knowledgeBaseId, DocumentId documentId) {
        client.delete(DeleteReq.builder()
                .collectionName(collection)
                .filter("knowledgeBaseId == '" + escapeFilterValue(knowledgeBaseId.value())
                        + "' && documentId == '" + escapeFilterValue(documentId.value()) + "'")
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
                                                  float[] queryEmbedding, int topK, boolean v2) {
        SearchReq.SearchReqBuilder<?, ?> builder = SearchReq.builder()
                .collectionName(collection)
                .data(List.of(new FloatVec(queryEmbedding)))
                .filter("knowledgeBaseId == '" + escapeFilterValue(knowledgeBaseId.value()) + "'")
                .topK(topK)
                .metricType(IndexParam.MetricType.COSINE)
                .outputFields(List.of("documentId", "documentName", "chunkIndex",
                        "chunkText", "startPosition", "endPosition"));
        if (v2) {
            builder.searchParams(Map.of("ef", hnswEf)).consistencyLevel(consistencyLevel);
        }
        SearchResp resp = client.search(builder.build());
        List<ChunkReference> results = new ArrayList<>();
        List<List<SearchResp.SearchResult>> batchResults = resp.getSearchResults();
        if (batchResults == null || batchResults.isEmpty()) {
            return results;
        }
        for (SearchResp.SearchResult result : batchResults.get(0)) {
            Map<String, Object> entity = result.getEntity();
            results.add(new ChunkReference(
                    (String) entity.get("documentId"),
                    (String) entity.get("documentName"),
                    ((Number) entity.get("chunkIndex")).intValue(),
                    (String) entity.get("chunkText"),
                    normalizeScore(result.getScore()),
                    ((Number) entity.get("startPosition")).intValue(),
                    ((Number) entity.get("endPosition")).intValue()
            ));
        }
        return results;
    }

    private List<JsonObject> toRows(KnowledgeBaseId knowledgeBaseId, DocumentId documentId, String documentName,
                                    List<DocumentChunk> chunks, List<float[]> embeddings) {
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

            JsonArray vec = new JsonArray();
            for (float f : embeddings.get(i)) {
                vec.add(f);
            }
            row.add("vector", vec);
            data.add(row);
        }
        return data;
    }

    private String escapeFilterValue(String value) {
        return value.replace("'", "\\'");
    }

    private float normalizeScore(Float score) {
        if (score == null) {
            return 0.0f;
        }
        return (score + 1.0f) / 2.0f;
    }
}
