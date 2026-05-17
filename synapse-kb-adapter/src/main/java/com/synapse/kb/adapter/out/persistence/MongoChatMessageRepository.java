package com.synapse.kb.adapter.out.persistence;

import com.synapse.kb.adapter.out.persistence.entity.ChatMessageDocument;
import com.synapse.kb.adapter.out.persistence.entity.ChatReferenceDocument;
import com.synapse.kb.model.ChatMessage;
import com.synapse.kb.model.ChatRole;
import com.synapse.kb.model.ChatSessionId;
import com.synapse.kb.model.ChunkReference;
import com.synapse.kb.model.KnowledgeBaseId;
import com.synapse.kb.repository.ChatMessageRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/** 聊天消息 MongoDB 仓储适配器。 */
@Component
public class MongoChatMessageRepository implements ChatMessageRepository {

    private final ChatMessageMongoRepository repository;

    public MongoChatMessageRepository(ChatMessageMongoRepository repository) {
        this.repository = repository;
    }

    @Override
    public ChatMessage save(ChatMessage message) {
        return toEntity(repository.save(toDocument(message)));
    }

    @Override
    public List<ChatMessage> findBySessionId(ChatSessionId sessionId, int page, int size) {
        return repository.findBySessionId(
                        sessionId.value(),
                        PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "sequence"))
                )
                .stream()
                .map(this::toEntity)
                .toList();
    }

    @Override
    public List<ChatMessage> findRecentBySessionIdBeforeOrEqual(ChatSessionId sessionId, long maxSequence, int limit) {
        return repository.findBySessionIdAndSequenceLessThanEqual(
                        sessionId.value(),
                        maxSequence,
                        PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "sequence"))
                )
                .stream()
                .map(this::toEntity)
                .sorted(Comparator.comparingLong(ChatMessage::sequence))
                .toList();
    }

    @Override
    public List<ChatMessage> findBySessionIdAndSequenceBetween(ChatSessionId sessionId, long fromExclusive, long toInclusive) {
        int limit = (int) Math.max(1, Math.min(toInclusive - fromExclusive, 500));
        return repository.findBySessionIdAndSequenceGreaterThanAndSequenceLessThanEqual(
                        sessionId.value(),
                        fromExclusive,
                        toInclusive,
                        PageRequest.of(0, limit, Sort.by(Sort.Direction.ASC, "sequence"))
                )
                .stream()
                .map(this::toEntity)
                .toList();
    }

    private ChatMessageDocument toDocument(ChatMessage message) {
        ChatMessageDocument doc = new ChatMessageDocument();
        doc.setId(message.id());
        doc.setSessionId(message.sessionId().value());
        doc.setOwnerUserId(message.ownerUserId());
        doc.setKnowledgeBaseId(message.knowledgeBaseId().value());
        doc.setRole(message.role().name());
        doc.setContent(message.content());
        doc.setReferences(message.references().stream().map(this::toReferenceDocument).toList());
        doc.setSequence(message.sequence());
        doc.setCreatedAt(message.createdAt());
        return doc;
    }

    private ChatMessage toEntity(ChatMessageDocument doc) {
        return new ChatMessage(
                doc.getId(),
                new ChatSessionId(doc.getSessionId()),
                doc.getOwnerUserId(),
                new KnowledgeBaseId(doc.getKnowledgeBaseId()),
                ChatRole.valueOf(doc.getRole()),
                doc.getContent(),
                doc.getReferences() == null
                        ? List.of()
                        : doc.getReferences().stream().map(this::toReference).toList(),
                doc.getSequence(),
                doc.getCreatedAt()
        );
    }

    private ChatReferenceDocument toReferenceDocument(ChunkReference reference) {
        ChatReferenceDocument doc = new ChatReferenceDocument();
        doc.setDocumentId(reference.documentId());
        doc.setDocumentName(reference.documentName());
        doc.setChunkIndex(reference.chunkIndex());
        doc.setChunkText(reference.chunkText());
        doc.setScore(reference.score());
        doc.setStartPosition(reference.startPosition());
        doc.setEndPosition(reference.endPosition());
        return doc;
    }

    private ChunkReference toReference(ChatReferenceDocument doc) {
        return new ChunkReference(
                doc.getDocumentId(),
                doc.getDocumentName(),
                doc.getChunkIndex(),
                doc.getChunkText(),
                doc.getScore(),
                doc.getStartPosition(),
                doc.getEndPosition()
        );
    }
}
