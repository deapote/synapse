package com.synapse.kb.adapter.out.persistence;

import com.synapse.kb.adapter.out.persistence.entity.KnowledgeBaseDocument;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

public interface KnowledgeBaseMongoRepository extends ReactiveMongoRepository<KnowledgeBaseDocument, String> {

}
