package com.synapse.kb.repository;

import com.synapse.kb.model.KnowledgeBase;
import com.synapse.kb.model.KnowledgeBaseId;

import java.util.List;
import java.util.Optional;

/**
 * 知识库聚合根仓储接口。
 *
 * <p>领域层只声明数据访问契约，不依赖任何持久化框架。
 * 具体实现由适配器层（如 {@code MongoKnowledgeBaseRepository}）提供。
 */
public interface KnowledgeBaseRepository {

    /**
     * 保存或更新知识库。
     *
     * @param knowledgeBase 待保存的知识库聚合根
     * @return 保存后的知识库（可能包含生成的 ID）
     */
    KnowledgeBase save(KnowledgeBase knowledgeBase);

    /**
     * 根据 ID 查询知识库。
     *
     * @param id 知识库唯一标识
     * @return 查询结果，不存在时返回 {@link Optional#empty()}
     */
    Optional<KnowledgeBase> findById(KnowledgeBaseId id);

    /**
     * 查询所有知识库。
     *
     * @return 知识库列表，无数据时返回空列表
     */
    List<KnowledgeBase> findAll();

    /**
     * 根据 ID 删除知识库。
     *
     * @param id 知识库唯一标识
     */
    void deleteById(KnowledgeBaseId id);
}
