package com.synapse.kb.adapter.out.persistence;

import com.synapse.kb.adapter.out.persistence.entity.DocumentDocument;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface DocumentMongoRepository extends ReactiveMongoRepository<DocumentDocument, String> {

    Flux<DocumentDocument> findByKnowledgeBaseId(String knowledgeBaseId);

    Mono<Boolean> existsByKnowledgeBaseIdAndContentHash(String knowledgeBaseId, String contentHash);
}
