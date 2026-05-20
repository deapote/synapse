package com.synapse.kb.port.out;

import com.synapse.kb.model.DocumentIndexRefreshJob;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 文档索引刷新任务出站端口。
 */
public interface DocumentIndexRefreshJobRepository {

    DocumentIndexRefreshJob save(DocumentIndexRefreshJob job);

    Optional<DocumentIndexRefreshJob> findById(String id);

    List<DocumentIndexRefreshJob> findPendingJobs(Instant before, int limit);

    Optional<DocumentIndexRefreshJob> claimNext(String workerId);

    void deleteByDocumentId(String documentId);
}
