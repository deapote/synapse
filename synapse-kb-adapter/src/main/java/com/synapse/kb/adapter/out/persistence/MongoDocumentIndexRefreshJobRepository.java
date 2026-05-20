package com.synapse.kb.adapter.out.persistence;

import com.synapse.kb.adapter.out.persistence.entity.DocumentIndexRefreshJobDocument;
import com.synapse.kb.model.DocumentId;
import com.synapse.kb.model.DocumentIndexRefreshJob;
import com.synapse.kb.model.DocumentIndexRefreshJobId;
import com.synapse.kb.model.DocumentIndexRefreshJobStatus;
import com.synapse.kb.model.KnowledgeBaseId;
import com.synapse.kb.port.out.DocumentIndexRefreshJobRepository;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Component
public class MongoDocumentIndexRefreshJobRepository implements DocumentIndexRefreshJobRepository {

    private final DocumentIndexRefreshJobMongoRepository jobMongoRepository;
    private final MongoTemplate mongoTemplate;

    public MongoDocumentIndexRefreshJobRepository(
            DocumentIndexRefreshJobMongoRepository jobMongoRepository,
            MongoTemplate mongoTemplate) {
        this.jobMongoRepository = jobMongoRepository;
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public DocumentIndexRefreshJob save(DocumentIndexRefreshJob job) {
        DocumentIndexRefreshJobDocument doc = toDocument(job);
        DocumentIndexRefreshJobDocument saved = jobMongoRepository.save(doc);
        return toEntity(saved);
    }

    @Override
    public Optional<DocumentIndexRefreshJob> findById(String id) {
        return jobMongoRepository.findById(id).map(this::toEntity);
    }

    @Override
    public List<DocumentIndexRefreshJob> findPendingJobs(Instant before, int limit) {
        Query query = new Query(
                Criteria.where("status").is(DocumentIndexRefreshJobStatus.PENDING.name())
                        .and("nextRunAt").lte(before)
        ).limit(limit).with(org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Direction.ASC, "createdAt"
        ));
        return mongoTemplate.find(query, DocumentIndexRefreshJobDocument.class)
                .stream()
                .map(this::toEntity)
                .toList();
    }

    @Override
    public Optional<DocumentIndexRefreshJob> claimNext(String workerId) {
        Instant now = Instant.now();

        Query query = new Query(
                new Criteria().orOperator(
                        Criteria.where("status").is(DocumentIndexRefreshJobStatus.PENDING.name()),
                        Criteria.where("status").is(DocumentIndexRefreshJobStatus.FAILED.name())
                                .and("nextRunAt").lte(now)
                )
        ).limit(1).with(org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Direction.ASC, "createdAt"
        ));

        Update update = new Update()
                .set("status", DocumentIndexRefreshJobStatus.RUNNING.name())
                .set("updatedAt", now)
                .inc("attempts", 1);

        DocumentIndexRefreshJobDocument doc = mongoTemplate.findAndModify(
                query, update, org.springframework.data.mongodb.core.FindAndModifyOptions.options().returnNew(true),
                DocumentIndexRefreshJobDocument.class
        );

        if (doc == null) {
            return Optional.empty();
        }
        return Optional.of(toEntity(doc));
    }

    @Override
    public void deleteByDocumentId(String documentId) {
        jobMongoRepository.deleteByDocumentId(documentId);
    }

    private DocumentIndexRefreshJobDocument toDocument(DocumentIndexRefreshJob job) {
        DocumentIndexRefreshJobDocument doc = new DocumentIndexRefreshJobDocument();
        doc.setId(job.getId().value());
        doc.setDocumentId(job.getDocumentId().value());
        doc.setKnowledgeBaseId(job.getKnowledgeBaseId().value());
        doc.setMetadataVersion(job.getMetadataVersion());
        doc.setStatus(job.getStatus().name());
        doc.setAttempts(job.getAttempts());
        doc.setFailureReason(job.getFailureReason());
        doc.setNextRunAt(job.getNextRunAt());
        doc.setCreatedAt(job.getCreatedAt());
        doc.setUpdatedAt(job.getUpdatedAt());
        return doc;
    }

    private DocumentIndexRefreshJob toEntity(DocumentIndexRefreshJobDocument doc) {
        return DocumentIndexRefreshJob.reconstruct(
                new DocumentIndexRefreshJobId(doc.getId()),
                new DocumentId(doc.getDocumentId()),
                new KnowledgeBaseId(doc.getKnowledgeBaseId()),
                doc.getMetadataVersion(),
                DocumentIndexRefreshJobStatus.valueOf(doc.getStatus()),
                doc.getAttempts(),
                doc.getFailureReason(),
                doc.getNextRunAt(),
                doc.getCreatedAt(),
                doc.getUpdatedAt()
        );
    }
}
