package com.synapse.kb.adapter.out.persistence;

import com.synapse.kb.adapter.out.persistence.entity.DocumentDocument;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

/**
 * 文档元数据 Spring Data MongoDB 仓储接口。
 */
public interface DocumentMongoRepository extends MongoRepository<DocumentDocument, String> {

    List<DocumentDocument> findByKnowledgeBaseId(String knowledgeBaseId);

    List<DocumentDocument> findByKnowledgeBaseId(String knowledgeBaseId, Pageable pageable);

    boolean existsByKnowledgeBaseIdAndContentHash(String knowledgeBaseId, String contentHash);

    List<DocumentDocument> findByKnowledgeBaseIdAndContentHash(String knowledgeBaseId, String contentHash);

    List<DocumentDocument> findBySupersedesDocumentId(String supersedesDocumentId);
}
