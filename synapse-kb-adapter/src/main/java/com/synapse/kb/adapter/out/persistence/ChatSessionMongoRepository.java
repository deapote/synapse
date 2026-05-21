package com.synapse.kb.adapter.out.persistence;

import com.synapse.kb.adapter.out.persistence.entity.ChatSessionDocument;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

/**
 * 聊天会话 Spring Data MongoDB 仓储接口。
 */
public interface ChatSessionMongoRepository extends MongoRepository<ChatSessionDocument, String> {

    List<ChatSessionDocument> findByOwnerUserIdAndKnowledgeBaseId(String ownerUserId, String knowledgeBaseId, Pageable pageable);
}
