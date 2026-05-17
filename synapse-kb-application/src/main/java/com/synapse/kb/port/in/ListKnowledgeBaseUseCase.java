package com.synapse.kb.port.in;

import com.synapse.kb.model.KnowledgeBase;

import java.util.List;

/** 查询可访问知识库入站端口。 */
public interface ListKnowledgeBaseUseCase {

    List<KnowledgeBase> listAll();

    List<KnowledgeBase> listAll(int page, int size);
}
