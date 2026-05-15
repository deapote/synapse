package com.synapse.kb.port.in;

import com.synapse.kb.model.Document;
import com.synapse.kb.model.KnowledgeBaseId;

import java.util.List;

/**
 * 列出知识库文档用例。
 *
 * <p>入站端口（Driving Port），用于知识库详情页展示其下所有文档。
 */
public interface ListDocumentUseCase {

    /**
     * 查询指定知识库下的所有文档。
     *
     * @param knowledgeBaseId 知识库 ID
     * @return 文档列表，无数据时返回空列表
     */
    List<Document> listByKnowledgeBase(KnowledgeBaseId knowledgeBaseId);

    /**
     * 分页查询指定知识库下的文档。
     *
     * @param knowledgeBaseId 知识库 ID
     * @param page            页码，从 0 开始
     * @param size            每页大小
     * @return 文档列表
     */
    List<Document> listByKnowledgeBase(KnowledgeBaseId knowledgeBaseId, int page, int size);
}
