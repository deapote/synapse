package com.synapse.kb.port.in;

import com.synapse.kb.model.KnowledgeBase;

import java.util.List;

/**
 * 列出所有知识库用例（入站端口）。
 *
 * <p>查询系统中所有已创建的知识库列表，按创建时间倒序排列。
 */
public interface ListKnowledgeBaseUseCase {

    /**
     * 查询所有知识库。
     *
     * @return 知识库列表，无数据时返回空列表
     */
    List<KnowledgeBase> listAll();
}
