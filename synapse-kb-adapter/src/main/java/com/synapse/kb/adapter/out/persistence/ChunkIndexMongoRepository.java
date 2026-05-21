package com.synapse.kb.adapter.out.persistence;

import com.synapse.kb.adapter.out.persistence.entity.ChunkIndexDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Collection;
import java.util.List;

/**
 * Chunk 关键词索引 Spring Data MongoDB 仓储接口。
 */
public interface ChunkIndexMongoRepository extends MongoRepository<ChunkIndexDocument, String> {

    List<ChunkIndexDocument> findByKnowledgeBaseIdAndTokensIn(String knowledgeBaseId, Collection<String> tokens);

    List<ChunkIndexDocument> findByKnowledgeBaseIdAndDocumentId(String knowledgeBaseId, String documentId);

    long countByKnowledgeBaseId(String knowledgeBaseId);

    void deleteByKnowledgeBaseIdAndDocumentId(String knowledgeBaseId, String documentId);
}
