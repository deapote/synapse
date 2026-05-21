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

/**
 * 知识库领域仓储实现，封装 Spring Data MongoDB 操作。
 * 负责 KnowledgeBase 聚合与 MongoDB 文档之间的映射及持久化。
 */
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
