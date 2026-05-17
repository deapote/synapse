package com.synapse.kb.adapter.out.persistence;

import com.synapse.kb.adapter.out.persistence.entity.ChatMessageDocument;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ChatMessageMongoRepository extends MongoRepository<ChatMessageDocument, String> {

    List<ChatMessageDocument> findBySessionId(String sessionId, Pageable pageable);

    List<ChatMessageDocument> findBySessionIdAndSequenceLessThanEqual(String sessionId, long sequence, Pageable pageable);

    List<ChatMessageDocument> findBySessionIdAndSequenceGreaterThanAndSequenceLessThanEqual(
            String sessionId,
            long fromExclusive,
            long toInclusive,
            Pageable pageable
    );
}
