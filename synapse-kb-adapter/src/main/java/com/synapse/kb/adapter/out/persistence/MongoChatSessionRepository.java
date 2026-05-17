package com.synapse.kb.adapter.out.persistence;

import com.synapse.kb.adapter.out.persistence.entity.ChatSessionDocument;
import com.synapse.kb.model.ChatSession;
import com.synapse.kb.model.ChatSessionId;
import com.synapse.kb.model.KnowledgeBaseId;
import com.synapse.kb.repository.ChatSessionRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** 聊天会话 MongoDB 仓储适配器。 */
@Component
public class MongoChatSessionRepository implements ChatSessionRepository {

    private final ChatSessionMongoRepository repository;

    public MongoChatSessionRepository(ChatSessionMongoRepository repository) {
        this.repository = repository;
    }

    @Override
    public ChatSession save(ChatSession session) {
        return toEntity(repository.save(toDocument(session)));
    }

    @Override
    public Optional<ChatSession> findById(ChatSessionId id) {
        return repository.findById(id.value()).map(this::toEntity);
    }

    @Override
    public Optional<ChatSession> findLatestByOwnerUserIdAndKnowledgeBaseId(String ownerUserId, KnowledgeBaseId knowledgeBaseId) {
        return repository.findByOwnerUserIdAndKnowledgeBaseId(
                        ownerUserId,
                        knowledgeBaseId.value(),
                        PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "updatedAt"))
                )
                .stream()
                .findFirst()
                .map(this::toEntity);
    }

    private ChatSessionDocument toDocument(ChatSession session) {
        ChatSessionDocument doc = new ChatSessionDocument();
        doc.setId(session.getId().value());
        doc.setOwnerUserId(session.getOwnerUserId());
        doc.setKnowledgeBaseId(session.getKnowledgeBaseId().value());
        doc.setTitle(session.getTitle());
        doc.setSummary(session.getSummary());
        doc.setSummarizedUntilSequence(session.getSummarizedUntilSequence());
        doc.setMessageCount(session.getMessageCount());
        doc.setCreatedAt(session.getCreatedAt());
        doc.setUpdatedAt(session.getUpdatedAt());
        return doc;
    }

    private ChatSession toEntity(ChatSessionDocument doc) {
        return ChatSession.reconstruct(
                new ChatSessionId(doc.getId()),
                doc.getOwnerUserId(),
                new KnowledgeBaseId(doc.getKnowledgeBaseId()),
                doc.getTitle(),
                doc.getSummary(),
                doc.getSummarizedUntilSequence(),
                doc.getMessageCount(),
                doc.getCreatedAt(),
                doc.getUpdatedAt()
        );
    }
}
