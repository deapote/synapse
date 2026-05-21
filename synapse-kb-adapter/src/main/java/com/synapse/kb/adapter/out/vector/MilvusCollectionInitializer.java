package com.synapse.kb.adapter.out.vector;

import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.AddFieldReq;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.GetLoadStateReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.collection.request.LoadCollectionReq;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class MilvusCollectionInitializer {

    private final MilvusClientV2 client;
    private final String v3CollectionName;
    private final int vectorDim;
    private final int hnswM;
    private final int hnswEfConstruction;
    private final long loadTimeoutMs;

    MilvusCollectionInitializer(MilvusClientV2 client, String v3CollectionName, int vectorDim,
                                int hnswM, int hnswEfConstruction, long loadTimeoutMs) {
        this.client = client;
        this.v3CollectionName = v3CollectionName;
        this.vectorDim = vectorDim;
        this.hnswM = hnswM;
        this.hnswEfConstruction = hnswEfConstruction;
        this.loadTimeoutMs = loadTimeoutMs;
    }

    void initV3Collection() {
        Boolean exists = client.hasCollection(HasCollectionReq.builder().collectionName(v3CollectionName).build());
        if (Boolean.TRUE.equals(exists)) {
            return;
        }

        CreateCollectionReq.CollectionSchema schema = CreateCollectionReq.CollectionSchema.builder().build();
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

    void loadCollectionIfNeeded(String collection) {
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
}
