package com.synapse.kb.port.in;

import com.synapse.kb.model.DocumentId;

import java.time.LocalDate;

/**
 * 替代旧资料。将旧文档标记为 SUPERSEDED，设置 effectiveTo；新文档保持 ACTIVE。
 */
public interface SupersedeDocumentUseCase {
    void supersede(DocumentId oldDocumentId, DocumentId newDocumentId, LocalDate effectiveTo);
}
