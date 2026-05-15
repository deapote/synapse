package com.synapse.kb.repository;

import com.synapse.kb.model.Document;
import com.synapse.kb.model.DocumentId;
import com.synapse.kb.model.KnowledgeBaseId;

import java.util.List;
import java.util.Optional;

/**
 * 文档聚合根仓储接口。
 *
 * <p>领域层只声明数据访问契约，不依赖任何持久化框架。
 * 具体实现由适配器层（如 {@code MongoDocumentRepository}）提供。
 */
public interface DocumentRepository {

    /**
     * 保存或更新文档。
     *
     * @param document 待保存的文档聚合根
     * @return 保存后的文档（可能包含生成的 ID）
     */
    Document save(Document document);

    /**
     * 根据 ID 查询文档。
     *
     * @param id 文档唯一标识
     * @return 查询结果，不存在时返回 {@link Optional#empty()}
     */
    Optional<Document> findById(DocumentId id);

    /**
     * 查询指定知识库下的所有文档。
     *
     * @param knowledgeBaseId 知识库唯一标识
     * @return 文档列表，无数据时返回空列表
     */
    List<Document> findByKnowledgeBaseId(KnowledgeBaseId knowledgeBaseId);

    /**
     * 分页查询指定知识库下的文档。
     *
     * @param knowledgeBaseId 知识库唯一标识
     * @param page            页码，从 0 开始
     * @param size            每页大小
     * @return 文档列表
     */
    List<Document> findByKnowledgeBaseId(KnowledgeBaseId knowledgeBaseId, int page, int size);

    /**
     * 根据 ID 删除文档。
     *
     * @param id 文档唯一标识
     */
    void deleteById(DocumentId id);

    /**
     * 检查指定知识库下是否存在相同内容哈希的文档，用于上传去重。
     *
     * @param knowledgeBaseId 知识库唯一标识
     * @param contentHash     文件内容哈希
     * @return 存在返回 {@code true}，否则 {@code false}
     */
    boolean existsByKnowledgeBaseIdAndContentHash(KnowledgeBaseId knowledgeBaseId, String contentHash);

    /**
     * 查询指定知识库下内容哈希匹配的所有文档。
     *
     * <p>用于判断是否可以重新上传（如失败重试场景）。
     *
     * @param knowledgeBaseId 知识库唯一标识
     * @param contentHash     文件内容哈希
     * @return 匹配的文档列表，无数据时返回空列表
     */
    List<Document> findByKnowledgeBaseIdAndContentHash(KnowledgeBaseId knowledgeBaseId, String contentHash);
}
