package com.synapse.kb.port.out;

import com.synapse.kb.model.DocumentId;
import com.synapse.kb.model.IngestionJob;
import com.synapse.kb.model.KnowledgeBaseId;

import java.time.Duration;
import java.util.Optional;

/**
 * 摄入作业仓储端口，由持久化适配器实现。
 * 管理文档摄入任务的保存、认领、查询与清理。
 */
public interface IngestionJobRepository {

    IngestionJob save(IngestionJob job);

    Optional<IngestionJob> findLatestByDocumentId(DocumentId documentId);

    Optional<IngestionJob> claimNext(String workerId, Duration leaseDuration);

    void deleteByDocumentId(DocumentId documentId);

    void deleteByKnowledgeBaseId(KnowledgeBaseId knowledgeBaseId);
}
