package com.synapse.kb.repository;

import com.synapse.kb.model.Document;
import com.synapse.kb.model.DocumentId;
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
}
