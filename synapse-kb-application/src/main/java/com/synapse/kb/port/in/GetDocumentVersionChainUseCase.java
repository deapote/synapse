package com.synapse.kb.port.in;

import com.synapse.kb.model.Document;
import com.synapse.kb.model.DocumentId;

import java.util.List;

/**
 * 获取文档版本链用例。
 * 按替代关系回溯并组装同一 canonicalKey 下的所有文档版本。
 */
public interface GetDocumentVersionChainUseCase {

    List<Document> getVersionChain(DocumentId id);
}
