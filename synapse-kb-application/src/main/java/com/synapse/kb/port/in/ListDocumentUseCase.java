package com.synapse.kb.port.in;

import com.synapse.kb.model.Document;
import com.synapse.kb.model.DocumentIndexStatus;
import com.synapse.kb.model.DocumentLifecycleStatus;
import com.synapse.kb.model.DocumentSourceType;
import com.synapse.kb.model.KnowledgeBaseId;

import java.util.List;

/** 查询知识库文档入站端口。 */
public interface ListDocumentUseCase {

    List<Document> listByKnowledgeBase(KnowledgeBaseId knowledgeBaseId);

    List<Document> listByKnowledgeBase(KnowledgeBaseId knowledgeBaseId, int page, int size);

    List<Document> listDocuments(ListDocumentQuery query);

    record ListDocumentQuery(
            KnowledgeBaseId knowledgeBaseId,
            int page,
            int size,
            DocumentSourceType sourceType,
            DocumentLifecycleStatus lifecycleStatus,
            DocumentIndexStatus indexStatus,
            String canonicalKey
    ) {
    }
}
