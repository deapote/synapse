package com.synapse.kb.port.in;

import com.synapse.kb.model.Document;
import com.synapse.kb.model.DocumentId;

import java.time.LocalDate;

public interface RetireDocumentUseCase {

    Document retire(DocumentId id, LocalDate effectiveTo);
}
