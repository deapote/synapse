package com.synapse.kb.port.in;

import com.synapse.kb.model.Document;
import com.synapse.kb.model.DocumentId;

public interface RetryDocumentIngestionUseCase {

    Document retry(DocumentId id);
}
