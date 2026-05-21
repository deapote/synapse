package com.synapse.kb.port.in;

import com.synapse.kb.model.Document;
import com.synapse.kb.model.DocumentId;

/**
 * 重新激活文档用例。
 * 将已退役（RETIRED）文档恢复为 ACTIVE 状态。
 */
public interface ReactivateDocumentUseCase {

    Document reactivate(DocumentId id);
}
