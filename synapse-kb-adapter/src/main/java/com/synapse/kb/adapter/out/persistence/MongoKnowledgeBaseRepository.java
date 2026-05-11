package com.synapse.kb.adapter.out.persistence;

import com.synapse.kb.adapter.out.persistence.entity.KnowledgeBaseDocument;
import com.synapse.kb.model.KnowledgeBase;
import com.synapse.kb.model.KnowledgeBaseId;
import com.synapse.kb.repository.KnowledgeBaseRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class MongoKnowledgeBaseRepository implements KnowledgeBaseRepository {

    private final KnowledgeBaseMongoRepository mongoRepository;

    public MongoKnowledgeBaseRepository(KnowledgeBaseMongoRepository mongoRepository) {
        this.mongoRepository = mongoRepository;
    }

    @Override
    public KnowledgeBase save(KnowledgeBase knowledgeBase) {
        KnowledgeBaseDocument doc = toDocument(knowledgeBase);
        KnowledgeBaseDocument saved = mongoRepository.save(doc).block();
        return toEntity(saved);
    }

    @Override
    public Optional<KnowledgeBase> findById(KnowledgeBaseId id) {
        return mongoRepository.findById(id.value())
                .blockOptional()
                .map(this::toEntity);
    }

    @Override
    public List<KnowledgeBase> findAll() {
        return mongoRepository.findAll()
                .toStream()
                .map(this::toEntity)
                .toList();
    }

    @Override
    public void deleteById(KnowledgeBaseId id) {
        mongoRepository.deleteById(id.value()).block();
    }

    private KnowledgeBaseDocument toDocument(KnowledgeBase kb) {
        KnowledgeBaseDocument doc = new KnowledgeBaseDocument();
        doc.setId(kb.getId().value());
        doc.setName(kb.getName());
        doc.setDescription(kb.getDescription());
        doc.setCreatedAt(kb.getCreatedAt());
        return doc;
    }

    private KnowledgeBase toEntity(KnowledgeBaseDocument doc) {
        return KnowledgeBase.reconstruct(
                new KnowledgeBaseId(doc.getId()),
                doc.getName(),
                doc.getDescription(),
                doc.getCreatedAt()
        );
    }
}
