package com.synapse.kb.port.in;

import com.synapse.kb.model.Document;
import com.synapse.kb.model.DocumentId;

import java.util.List;

public interface GetDocumentVersionChainUseCase {

    List<Document> getVersionChain(DocumentId id);
}
