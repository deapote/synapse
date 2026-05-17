package com.synapse.kb.port.out;

import com.synapse.kb.model.DocumentId;
import com.synapse.kb.model.IngestionJob;
import com.synapse.kb.model.KnowledgeBaseId;

import java.time.Duration;
import java.util.Optional;

public interface IngestionJobRepository {

    IngestionJob save(IngestionJob job);

    Optional<IngestionJob> findLatestByDocumentId(DocumentId documentId);

    Optional<IngestionJob> claimNext(String workerId, Duration leaseDuration);

    void deleteByDocumentId(DocumentId documentId);

    void deleteByKnowledgeBaseId(KnowledgeBaseId knowledgeBaseId);
}
