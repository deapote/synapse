package com.synapse.kb.adapter.out.persistence;

import com.synapse.kb.adapter.out.persistence.entity.ChatSessionDocument;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ChatSessionMongoRepository extends MongoRepository<ChatSessionDocument, String> {

    List<ChatSessionDocument> findByOwnerUserIdAndKnowledgeBaseId(String ownerUserId, String knowledgeBaseId, Pageable pageable);
}
