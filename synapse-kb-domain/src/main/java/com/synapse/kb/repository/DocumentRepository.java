package com.synapse.kb.repository;

import com.synapse.kb.model.Document;
import com.synapse.kb.model.DocumentId;
import com.synapse.kb.model.DocumentLifecycleStatus;
import com.synapse.kb.model.KnowledgeBaseId;

import java.util.List;
import java.util.Optional;

/** 文档聚合根仓储契约。 */
public interface DocumentRepository {

    Document save(Document document);

    Optional<Document> findById(DocumentId id);

    List<Document> findByKnowledgeBaseId(KnowledgeBaseId knowledgeBaseId);

    List<Document> findByKnowledgeBaseId(KnowledgeBaseId knowledgeBaseId, int page, int size);

    void deleteById(DocumentId id);

    boolean existsByKnowledgeBaseIdAndContentHash(KnowledgeBaseId knowledgeBaseId, String contentHash);

    /** 返回所有同哈希记录，用于区分重复上传和失败重试。 */
    List<Document> findByKnowledgeBaseIdAndContentHash(KnowledgeBaseId knowledgeBaseId, String contentHash);

    /** 按知识库 + canonicalKey + 时效状态查找文档，用于版本替代和检索去重。 */
    List<Document> findByKnowledgeBaseIdAndCanonicalKeyAndLifecycleStatus(
            KnowledgeBaseId knowledgeBaseId, String canonicalKey, DocumentLifecycleStatus status);

    /** 查找替代了指定文档的所有文档。 */
    List<Document> findBySupersedesDocumentId(DocumentId documentId);

    /** 按复合条件查询文档（数据库层过滤+分页）。 */
    List<Document> findByCriteria(DocumentQueryCriteria criteria);

    /** 按复合条件统计文档数量。 */
    long countByCriteria(DocumentQueryCriteria criteria);
}
