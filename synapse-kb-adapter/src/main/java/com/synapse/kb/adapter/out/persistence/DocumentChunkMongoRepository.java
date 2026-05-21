package com.synapse.kb.adapter.out.persistence;

import com.synapse.kb.adapter.out.persistence.entity.DocumentChunkDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

/**
 * 文档 Chunk 内容 Spring Data MongoDB 仓储接口。
 */
public interface DocumentChunkMongoRepository extends MongoRepository<DocumentChunkDocument, String> {

    List<DocumentChunkDocument> findByDocumentId(String documentId);

    void deleteByDocumentId(String documentId);
}
