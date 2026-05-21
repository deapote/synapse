package com.synapse.kb.port.in;

import com.synapse.kb.model.Document;
import com.synapse.kb.model.DocumentId;

import java.time.LocalDate;

/**
 * 退役文档用例。
 * 将 ACTIVE 文档标记为 RETIRED，并指定生效截止日期。
 */
public interface RetireDocumentUseCase {

    Document retire(DocumentId id, LocalDate effectiveTo);
}
