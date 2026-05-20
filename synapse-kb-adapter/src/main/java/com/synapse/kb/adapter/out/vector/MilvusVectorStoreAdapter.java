package com.synapse.kb.adapter.out.vector;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.synapse.kb.model.ChunkReference;
import com.synapse.kb.model.DocumentChunk;
import com.synapse.kb.model.DocumentId;
import com.synapse.kb.model.DocumentLifecycleStatus;
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

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class MilvusVectorStoreAdapter implements VectorStorePort {

    private final String host;
    private final int port;
    private final String v3CollectionName;
    private final int vectorDim;
    private final long connectTimeoutMs;
    private final long rpcDeadlineMs;
    private final int hnswM;
    private final int hnswEfConstruction;
    private final int hnswEf;
    private final long loadTimeoutMs;
    private final ConsistencyLevel consistencyLevel;
    private final io.micrometer.core.instrument.Timer searchTimer;

    private volatile MilvusClientV2 client;
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    public MilvusVectorStoreAdapter(
            @Value("${milvus.host:127.0.0.1}") String host,
            @Value("${milvus.port:19530}") int port,
            @Value("${milvus.v3.collection-name:synapse_document_chunks_v3}") String v3CollectionName,
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
        this.v3CollectionName = v3CollectionName;
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
            initV3Collection();
            loadCollectionIfNeeded(v3CollectionName);
            initialized.set(true);
        }
    }

    private void initV3Collection() {
        Boolean exists = client.hasCollection(HasCollectionReq.builder().collectionName(v3CollectionName).build());
        if (Boolean.TRUE.equals(exists)) {
            return;
        }

        CreateCollectionReq.CollectionSchema schema = client.createSchema();
        schema.addField(AddFieldReq.builder()
                .fieldName("id").dataType(DataType.VarChar)
                .isPrimaryKey(true).maxLength(64).build());
        schema.addField(AddFieldReq.builder()
                .fieldName("knowledgeBaseId").dataType(DataType.VarChar)
                .isPartitionKey(true).maxLength(64).build());
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
        schema.addField(AddFieldReq.builder()
                .fieldName("effectiveFromEpochDay").dataType(DataType.Int64).build());
        schema.addField(AddFieldReq.builder()
                .fieldName("effectiveToEpochDay").dataType(DataType.Int64).build());
        schema.addField(AddFieldReq.builder()
                .fieldName("lifecycleStatus").dataType(DataType.VarChar)
                .maxLength(16).build());
        schema.addField(AddFieldReq.builder()
                .fieldName("canonicalKey").dataType(DataType.VarChar)
                .maxLength(256).build());
        schema.addField(AddFieldReq.builder()
                .fieldName("versionLabel").dataType(DataType.VarChar)
                .maxLength(64).build());
        schema.addField(AddFieldReq.builder()
                .fieldName("authorityLevel").dataType(DataType.Int32).build());
        schema.addField(AddFieldReq.builder()
                .fieldName("jurisdiction").dataType(DataType.VarChar)
                .maxLength(128).build());
        schema.addField(AddFieldReq.builder()
                .fieldName("sourceType").dataType(DataType.VarChar)
                .maxLength(16).build());

        List<IndexParam> indexes = new ArrayList<>();
        indexes.add(IndexParam.builder()
                .fieldName("vector")
                .indexType(IndexParam.IndexType.HNSW)
                .metricType(IndexParam.MetricType.COSINE)
                .extraParams(Map.of("M", hnswM, "efConstruction", hnswEfConstruction))
                .build());
        indexes.add(IndexParam.builder()
                .fieldName("knowledgeBaseId")
                .indexType(IndexParam.IndexType.TRIE)
                .build());
        indexes.add(IndexParam.builder()
                .fieldName("documentId")
                .indexType(IndexParam.IndexType.TRIE)
                .build());
        indexes.add(IndexParam.builder()
                .fieldName("effectiveFromEpochDay")
                .indexType(IndexParam.IndexType.STL_SORT)
                .build());
        indexes.add(IndexParam.builder()
                .fieldName("effectiveToEpochDay")
                .indexType(IndexParam.IndexType.STL_SORT)
                .build());
        indexes.add(IndexParam.builder()
                .fieldName("lifecycleStatus")
                .indexType(IndexParam.IndexType.TRIE)
                .build());
        indexes.add(IndexParam.builder()
                .fieldName("canonicalKey")
                .indexType(IndexParam.IndexType.TRIE)
                .build());
        indexes.add(IndexParam.builder()
                .fieldName("sourceType")
                .indexType(IndexParam.IndexType.TRIE)
                .build());

        client.createCollection(CreateCollectionReq.builder()
                .collectionName(v3CollectionName)
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
                      List<DocumentChunk> chunks, List<float[]> embeddings, DocumentMetadata metadata) {
        ensureInitialized();
        if (chunks.size() != embeddings.size()) {
            throw new DomainException("分块数量与向量数量不匹配: " + chunks.size() + " vs " + embeddings.size());
        }
        List<JsonObject> data = toRows(knowledgeBaseId, documentId, documentName, chunks, embeddings, metadata);
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
        // v2 使用异步索引刷新任务，不在请求线程中直接更新
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
                .filter("knowledgeBaseId == '" + safeFilterValue(knowledgeBaseId.value())
                        + "' && documentId == '" + safeFilterValue(documentId.value()) + "'")
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
        String filterExpr = buildSearchFilter(knowledgeBaseId, filter);
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
            results.add(toChunkReference(entity, normalizeScore(result.getScore())));
        }
        return results;
    }

    private String buildSearchFilter(KnowledgeBaseId knowledgeBaseId, RetrievalFilter filter) {
        long asOfEpochDay = filter.asOfDate().toEpochDay();
        StringBuilder sb = new StringBuilder();
        sb.append("knowledgeBaseId == '").append(safeFilterValue(knowledgeBaseId.value())).append("'");
        sb.append(" && effectiveFromEpochDay <= ").append(asOfEpochDay);
        sb.append(" && (effectiveToEpochDay == ").append(Long.MAX_VALUE).append(" || effectiveToEpochDay > ").append(asOfEpochDay).append(")");
        sb.append(" && lifecycleStatus in ['ACTIVE', 'SUPERSEDED']");
        if (filter.sourceType() != null) {
            sb.append(" && sourceType == '").append(safeFilterValue(filter.sourceType().name())).append("'");
        }
        if (filter.jurisdiction() != null && !filter.jurisdiction().isBlank()) {
            sb.append(" && jurisdiction == '").append(safeFilterValue(filter.jurisdiction())).append("'");
        }
        return sb.toString();
    }

    private List<JsonObject> toRows(KnowledgeBaseId knowledgeBaseId, DocumentId documentId, String documentName,
                                    List<DocumentChunk> chunks, List<float[]> embeddings, DocumentMetadata metadata) {
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
            row.addProperty("effectiveFromEpochDay", effectiveFromEpochDay);
            row.addProperty("effectiveToEpochDay", effectiveToEpochDay);
            row.addProperty("lifecycleStatus", lifecycleStatus);
            row.addProperty("canonicalKey", canonicalKey);
            row.addProperty("versionLabel", versionLabel);
            row.addProperty("authorityLevel", authorityLevel);
            row.addProperty("jurisdiction", jurisdiction);
            row.addProperty("sourceType", sourceType);

            JsonArray vec = new JsonArray();
            for (float f : embeddings.get(i)) {
                vec.add(f);
            }
            row.add("vector", vec);
            data.add(row);
        }
        return data;
    }

    private ChunkReference toChunkReference(Map<String, Object> entity, float score) {
        String lifecycleStatusStr = (String) entity.get("lifecycleStatus");
        DocumentLifecycleStatus lifecycleStatus = lifecycleStatusStr != null && !lifecycleStatusStr.isBlank()
                ? DocumentLifecycleStatus.valueOf(lifecycleStatusStr)
                : DocumentLifecycleStatus.ACTIVE;

        Long effectiveFromEpochDay = entity.get("effectiveFromEpochDay") instanceof Number
                ? ((Number) entity.get("effectiveFromEpochDay")).longValue()
                : null;
        Long effectiveToEpochDay = entity.get("effectiveToEpochDay") instanceof Number
                ? ((Number) entity.get("effectiveToEpochDay")).longValue()
                : null;

        LocalDate effectiveFrom = effectiveFromEpochDay != null && effectiveFromEpochDay != Long.MAX_VALUE
                ? LocalDate.ofEpochDay(effectiveFromEpochDay)
                : null;
        LocalDate effectiveTo = effectiveToEpochDay != null && effectiveToEpochDay != Long.MAX_VALUE
                ? LocalDate.ofEpochDay(effectiveToEpochDay)
                : null;

        Integer authorityLevel = entity.get("authorityLevel") instanceof Number
                ? ((Number) entity.get("authorityLevel")).intValue()
                : 0;

        return new ChunkReference(
                (String) entity.get("documentId"),
                (String) entity.get("documentName"),
                ((Number) entity.get("chunkIndex")).intValue(),
                (String) entity.get("chunkText"),
                score,
                ((Number) entity.get("startPosition")).intValue(),
                ((Number) entity.get("endPosition")).intValue(),
                (String) entity.get("canonicalKey"),
                (String) entity.get("versionLabel"),
                effectiveFrom,
                effectiveTo,
                lifecycleStatus,
                authorityLevel,
                (String) entity.get("jurisdiction")
        );
    }

    private String safeFilterValue(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_\\-:]+")) {
            throw new DomainException("非法的向量过滤条件: " + value);
        }
        return value;
    }

    private float normalizeScore(Float score) {
        if (score == null) {
            return 0.0f;
        }
        return (score + 1.0f) / 2.0f;
    }
}
