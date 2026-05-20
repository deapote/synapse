package com.synapse.kb.adapter.out.persistence;

import com.synapse.kb.adapter.out.persistence.entity.DocumentChunkDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface DocumentChunkMongoRepository extends MongoRepository<DocumentChunkDocument, String> {

    List<DocumentChunkDocument> findByDocumentId(String documentId);

    void deleteByDocumentId(String documentId);
}
