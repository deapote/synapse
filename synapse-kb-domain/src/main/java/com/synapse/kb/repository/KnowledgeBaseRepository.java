package com.synapse.kb.repository;

import com.synapse.kb.model.KnowledgeBase;
import com.synapse.kb.model.KnowledgeBaseId;

import java.util.List;
import java.util.Optional;

/** 知识库聚合根仓储契约。 */
public interface KnowledgeBaseRepository {

    KnowledgeBase save(KnowledgeBase knowledgeBase);

    Optional<KnowledgeBase> findById(KnowledgeBaseId id);

    List<KnowledgeBase> findAll();

    List<KnowledgeBase> findAll(int page, int size);

    void deleteById(KnowledgeBaseId id);
}
