package com.synapse.kb.port.in;

import com.synapse.kb.model.Document;
import com.synapse.kb.model.KnowledgeBaseId;

import java.util.List;

/** 查询知识库文档入站端口。 */
public interface ListDocumentUseCase {

    List<Document> listByKnowledgeBase(KnowledgeBaseId knowledgeBaseId);

    List<Document> listByKnowledgeBase(KnowledgeBaseId knowledgeBaseId, int page, int size);
}
