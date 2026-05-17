package com.synapse.kb.adapter.out.persistence;

import com.synapse.kb.adapter.out.persistence.entity.ChunkIndexDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Collection;
import java.util.List;

public interface ChunkIndexMongoRepository extends MongoRepository<ChunkIndexDocument, String> {

    List<ChunkIndexDocument> findByKnowledgeBaseIdAndTokensIn(String knowledgeBaseId, Collection<String> tokens);

    long countByKnowledgeBaseId(String knowledgeBaseId);

    void deleteByKnowledgeBaseIdAndDocumentId(String knowledgeBaseId, String documentId);
}
