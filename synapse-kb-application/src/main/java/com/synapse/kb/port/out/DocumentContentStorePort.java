package com.synapse.kb.port.out;

import com.synapse.kb.model.DocumentId;
import com.synapse.kb.model.KnowledgeBaseId;

import java.io.InputStream;

public interface DocumentContentStorePort {

    String store(KnowledgeBaseId knowledgeBaseId, DocumentId documentId, String fileName,
                 String contentType, InputStream content);

    InputStream open(String contentObjectId);

    void delete(String contentObjectId);
}
