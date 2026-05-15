package com.synapse.kb.adapter.out.persistence;

import com.synapse.kb.adapter.out.persistence.entity.KnowledgeBaseDocument;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface KnowledgeBaseMongoRepository extends MongoRepository<KnowledgeBaseDocument, String> {

    List<KnowledgeBaseDocument> findAllBy(Pageable pageable);
}
