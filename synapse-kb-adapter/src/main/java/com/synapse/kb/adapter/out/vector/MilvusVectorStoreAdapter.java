package com.synapse.kb.adapter.out.vector;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.synapse.kb.model.ChunkReference;
import com.synapse.kb.model.DocumentChunk;
import com.synapse.kb.model.DocumentId;
import com.synapse.kb.model.KnowledgeBaseId;
import com.synapse.kb.port.out.VectorStorePort;
import com.synapse.shared.exception.DomainException;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.AddFieldReq;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
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

/**
 * Milvus 向量存储适配器。客户端懒初始化，避免启动强依赖 Milvus。
 */
@Component
public class MilvusVectorStoreAdapter implements VectorStorePort {

    private final String host;
    private final int port;
    private final String collectionName;
    private final int vectorDim;
    private final long connectTimeoutMs;
    private final long rpcDeadlineMs;

    private MilvusClientV2 client;
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    public MilvusVectorStoreAdapter(
            @Value("${milvus.host:127.0.0.1}") String host,
            @Value("${milvus.port:19530}") int port,
            @Value("${milvus.collection-name:synapse_document_chunks}") String collectionName,
            @Value("${milvus.embedding-dimension:1536}") int vectorDim,
            @Value("${milvus.connect-timeout-ms:5000}") long connectTimeoutMs,
            @Value("${milvus.rpc-deadline-ms:30000}") long rpcDeadlineMs) {
        this.host = host;
        this.port = port;
        this.collectionName = collectionName;
        this.vectorDim = vectorDim;
        this.connectTimeoutMs = connectTimeoutMs;
        this.rpcDeadlineMs = rpcDeadlineMs;
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
            initCollection();
            initialized.set(true);
        }
    }

    private void initCollection() {
        Boolean exists = client.hasCollection(
                HasCollectionReq.builder().collectionName(collectionName).build()
        );
        if (Boolean.TRUE.equals(exists)) {
            return;
        }

        CreateCollectionReq.CollectionSchema schema = client.createSchema();
        schema.addField(AddFieldReq.builder()
                .fieldName("id").dataType(DataType.VarChar)
                .isPrimaryKey(true).maxLength(64).build());
        schema.addField(AddFieldReq.builder()
                .fieldName("knowledgeBaseId").dataType(DataType.VarChar)
                .maxLength(64).build());
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
                .indexType(IndexParam.IndexType.IVF_FLAT)
                .metricType(IndexParam.MetricType.COSINE)
                .extraParams(Map.of("nlist", 128))
                .build());

        client.createCollection(CreateCollectionReq.builder()
                .collectionName(collectionName)
                .collectionSchema(schema)
                .indexParams(indexes)
                .build());
    }

    @Override
    public void store(KnowledgeBaseId knowledgeBaseId, DocumentId documentId, String documentName,
                      List<DocumentChunk> chunks, List<float[]> embeddings) {
        ensureInitialized();
        if (chunks.size() != embeddings.size()) {
            throw new DomainException("分块数量与向量数量不匹配: " + chunks.size() + " vs " + embeddings.size());
        }

        List<JsonObject> data = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            DocumentChunk chunk = chunks.get(i);
            float[] embedding = embeddings.get(i);

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
            for (float f : embedding) {
                vec.add(f);
            }
            row.add("vector", vec);

            data.add(row);
        }

        try {
            client.insert(InsertReq.builder()
                    .collectionName(collectionName)
                    .data(data)
                    .build());
        } catch (Exception e) {
            throw new DomainException("向量存储失败", e);
        }
    }

    @Override
    public List<ChunkReference> search(KnowledgeBaseId knowledgeBaseId, float[] queryEmbedding, int topK) {
        ensureInitialized();
        try {
            SearchReq req = SearchReq.builder()
                    .collectionName(collectionName)
                    .data(List.of(new FloatVec(queryEmbedding)))
                    .filter("knowledgeBaseId == '" + escapeFilterValue(knowledgeBaseId.value()) + "'")
                    .topK(topK)
                    .outputFields(List.of("documentId", "documentName", "chunkIndex",
                            "chunkText", "startPosition", "endPosition"))
                    .build();

            SearchResp resp = client.search(req);
            List<ChunkReference> results = new ArrayList<>();

            List<List<SearchResp.SearchResult>> batchResults = resp.getSearchResults();
            if (batchResults == null || batchResults.isEmpty()) {
                return results;
            }

            for (SearchResp.SearchResult result : batchResults.get(0)) {
                Map<String, Object> entity = result.getEntity();
                float score = normalizeScore(result.getScore());

                results.add(new ChunkReference(
                        (String) entity.get("documentId"),
                        (String) entity.get("documentName"),
                        ((Number) entity.get("chunkIndex")).intValue(),
                        (String) entity.get("chunkText"),
                        score,
                        ((Number) entity.get("startPosition")).intValue(),
                        ((Number) entity.get("endPosition")).intValue()
                ));
            }

            return results;
        } catch (Exception e) {
            throw new DomainException("向量检索失败", e);
        }
    }

    @Override
    public void deleteByDocumentId(KnowledgeBaseId knowledgeBaseId, DocumentId documentId) {
        ensureInitialized();
        try {
            client.delete(DeleteReq.builder()
                    .collectionName(collectionName)
                    .filter("knowledgeBaseId == '" + escapeFilterValue(knowledgeBaseId.value())
                            + "' && documentId == '" + escapeFilterValue(documentId.value()) + "'")
                    .build());
        } catch (Exception e) {
            throw new DomainException("向量删除失败", e);
        }
    }

    /** 转义 Milvus filter 值，防止表达式注入。 */
    private String escapeFilterValue(String value) {
        return value.replace("'", "\\'");
    }

    /** 将 COSINE 分数从 [-1, 1] 映射到 [0, 1]。 */
    private float normalizeScore(Float score) {
        if (score == null) {
            return 0.0f;
        }
        return (score + 1.0f) / 2.0f;
    }
}
