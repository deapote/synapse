package com.synapse.kb.adapter.out.vector;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.synapse.kb.model.DocumentChunk;
import com.synapse.kb.model.DocumentId;
import com.synapse.kb.model.KnowledgeBaseId;
import com.synapse.kb.port.out.ChunkSearchResult;
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
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Milvus 向量存储适配器。
 *
 * <p>实现 {@link VectorStorePort}，通过 Milvus SDK v2 操作向量数据库，
 * 负责文档块向量的存储、相似度检索和按文档删除。
 *
 * <p>Collection 名称固定为 {@code synapse_document_chunks}，向量维度 1536，
 * 使用 IVF_FLAT 索引和 COSINE 距离度量。启动时自动检查并创建 Collection。
 */
@Component
public class MilvusVectorStoreAdapter implements VectorStorePort {

    private static final String COLLECTION_NAME = "synapse_document_chunks";
    private static final int VECTOR_DIM = 1536;

    private MilvusClientV2 client;
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    public MilvusVectorStoreAdapter() {
        // 构造函数不做任何网络操作，延迟到第一次使用时初始化
    }

    /**
     * 延迟初始化 Milvus 客户端和 Collection。
     *
     * <p>不在构造函数中执行，避免 Milvus 未启动时导致整个 Spring 上下文初始化失败。
     * 第一次执行 {@code store} / {@code search} / {@code delete} 时才连接服务器并建表。
     */
    private void ensureInitialized() {
        if (initialized.get()) {
            return;
        }
        synchronized (this) {
            if (initialized.get()) {
                return;
            }
            ConnectConfig config = ConnectConfig.builder()
                    .uri("http://localhost:19530")
                    .build();
            this.client = new MilvusClientV2(config);
            initCollection();
            initialized.set(true);
        }
    }

    /**
     * 初始化 Collection：如果不存在则创建 Schema、索引并加载。
     */
    private void initCollection() {
        Boolean exists = client.hasCollection(
                HasCollectionReq.builder().collectionName(COLLECTION_NAME).build()
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
                .dimension(VECTOR_DIM).build());
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
                .collectionName(COLLECTION_NAME)
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
                    .collectionName(COLLECTION_NAME)
                    .data(data)
                    .build());
        } catch (Exception e) {
            throw new DomainException("向量存储失败", e);
        }
    }

    @Override
    public List<ChunkSearchResult> search(KnowledgeBaseId knowledgeBaseId, float[] queryEmbedding, int topK) {
        ensureInitialized();
        try {
            SearchReq req = SearchReq.builder()
                    .collectionName(COLLECTION_NAME)
                    .data(List.of(new FloatVec(queryEmbedding)))
                    .filter("knowledgeBaseId == '" + knowledgeBaseId.value() + "'")
                    .topK(topK)
                    .outputFields(List.of("documentId", "documentName", "chunkText", "startPosition", "endPosition"))
                    .build();

            SearchResp resp = client.search(req);
            List<ChunkSearchResult> results = new ArrayList<>();

            List<List<SearchResp.SearchResult>> batchResults = resp.getSearchResults();
            if (batchResults == null || batchResults.isEmpty()) {
                return results;
            }

            // 只传了一个查询向量，batchResults 只有一层
            for (SearchResp.SearchResult result : batchResults.get(0)) {
                Map<String, Object> entity = result.getEntity();
                float score = normalizeScore(result.getScore());

                results.add(new ChunkSearchResult(
                        (String) entity.get("documentId"),
                        (String) entity.get("documentName"),
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
                    .collectionName(COLLECTION_NAME)
                    .filter("documentId == '" + documentId.value() + "'")
                    .build());
        } catch (Exception e) {
            throw new DomainException("向量删除失败", e);
        }
    }

    /**
     * 将 Milvus COSINE 分数从 [-1, 1] 映射到 [0, 1]。
     *
     * @param score 原始 COSINE 分数
     * @return 归一化后的分数
     */
    private float normalizeScore(Float score) {
        if (score == null) {
            return 0.0f;
        }
        return (score + 1.0f) / 2.0f;
    }
}
