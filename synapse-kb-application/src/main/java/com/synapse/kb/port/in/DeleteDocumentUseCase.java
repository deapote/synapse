package com.synapse.kb.port.in;

import com.synapse.kb.model.DocumentId;

/** 删除文档入站端口。 */
public interface DeleteDocumentUseCase {

    void delete(DocumentId id);
}
