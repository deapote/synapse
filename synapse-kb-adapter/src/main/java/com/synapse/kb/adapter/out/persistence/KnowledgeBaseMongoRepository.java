package com.synapse.kb.adapter.out.persistence;

import com.synapse.kb.adapter.out.persistence.entity.KnowledgeBaseDocument;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

/**
 * 知识库 Spring Data MongoDB 仓储接口。
 */
public interface KnowledgeBaseMongoRepository extends MongoRepository<KnowledgeBaseDocument, String> {

    List<KnowledgeBaseDocument> findAllBy(Pageable pageable);
}
