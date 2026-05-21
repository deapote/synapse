package com.synapse.kb.adapter.out.persistence;

import com.synapse.kb.adapter.out.persistence.entity.DocumentIndexRefreshJobDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;

/**
 * 文档索引刷新任务 Spring Data MongoDB 仓储接口。
 */
public interface DocumentIndexRefreshJobMongoRepository extends MongoRepository<DocumentIndexRefreshJobDocument, String> {

    List<DocumentIndexRefreshJobDocument> findByStatusAndNextRunAtLessThanEqualOrderByCreatedAtAsc(
            String status, Instant nextRunAt);

    List<DocumentIndexRefreshJobDocument> findByDocumentId(String documentId);

    void deleteByDocumentId(String documentId);
}
