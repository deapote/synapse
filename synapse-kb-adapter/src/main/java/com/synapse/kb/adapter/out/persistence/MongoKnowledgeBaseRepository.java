package com.synapse.kb.adapter.out.persistence;

import com.synapse.kb.adapter.out.persistence.entity.KnowledgeBaseDocument;
import com.synapse.kb.model.KnowledgeBase;
import com.synapse.kb.model.KnowledgeBaseId;
import com.synapse.kb.repository.KnowledgeBaseRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
        KnowledgeBaseDocument saved = mongoRepository.save(doc);
        return toEntity(saved);
    }

    @Override
    public Optional<KnowledgeBase> findById(KnowledgeBaseId id) {
        return mongoRepository.findById(id.value())
                .map(this::toEntity);
    }

    @Override
    public List<KnowledgeBase> findAll() {
        return mongoRepository.findAll()
                .stream()
                .map(this::toEntity)
                .toList();
    }

    @Override
    public List<KnowledgeBase> findAll(int page, int size) {
        return mongoRepository.findAllBy(
                        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
                )
                .stream()
                .map(this::toEntity)
                .toList();
    }

    @Override
    public void deleteById(KnowledgeBaseId id) {
        mongoRepository.deleteById(id.value());
    }

    private KnowledgeBaseDocument toDocument(KnowledgeBase kb) {
        KnowledgeBaseDocument doc = new KnowledgeBaseDocument();
        doc.setId(kb.getId().value());
        doc.setName(kb.getName());
        doc.setDescription(kb.getDescription());
        doc.setOwnerUserId(kb.getOwnerUserId());
        doc.setCreatedAt(kb.getCreatedAt());
        return doc;
    }

    private KnowledgeBase toEntity(KnowledgeBaseDocument doc) {
        return KnowledgeBase.reconstruct(
                new KnowledgeBaseId(doc.getId()),
                doc.getName(),
                doc.getDescription(),
                doc.getOwnerUserId(),
                doc.getCreatedAt()
        );
    }
}
