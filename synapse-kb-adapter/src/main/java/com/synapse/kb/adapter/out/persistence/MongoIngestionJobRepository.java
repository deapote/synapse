package com.synapse.kb.adapter.out.persistence;

import com.synapse.kb.adapter.out.persistence.entity.IngestionJobDocument;
import com.synapse.kb.model.DocumentId;
import com.synapse.kb.model.IngestionJob;
import com.synapse.kb.model.IngestionJobStatus;
import com.synapse.kb.model.KnowledgeBaseId;
import com.synapse.kb.port.out.IngestionJobRepository;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Component
public class MongoIngestionJobRepository implements IngestionJobRepository {

    private final MongoTemplate mongoTemplate;

    public MongoIngestionJobRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public IngestionJob save(IngestionJob job) {
        IngestionJobDocument saved = mongoTemplate.save(toDocument(job));
        return toEntity(saved);
    }

    @Override
    public Optional<IngestionJob> findLatestByDocumentId(DocumentId documentId) {
        Query query = new Query(Criteria.where("documentId").is(documentId.value()))
                .with(Sort.by(Sort.Direction.DESC, "createdAt"))
                .limit(1);
        return Optional.ofNullable(mongoTemplate.findOne(query, IngestionJobDocument.class)).map(this::toEntity);
    }

    @Override
    public Optional<IngestionJob> claimNext(String workerId, Duration leaseDuration) {
        Instant now = Instant.now();
        Criteria runnable = new Criteria().andOperator(
                Criteria.where("status").in(IngestionJobStatus.QUEUED.name(), IngestionJobStatus.RETRYING.name()),
                Criteria.where("nextRunAt").lte(now)
        );
        Criteria expiredLease = new Criteria().andOperator(
                Criteria.where("status").is(IngestionJobStatus.RUNNING.name()),
                Criteria.where("leaseExpiresAt").lt(now)
        );
        Query query = new Query(new Criteria().orOperator(runnable, expiredLease))
                .with(Sort.by(Sort.Direction.ASC, "nextRunAt"))
                .limit(1);
        Update update = new Update()
                .set("status", IngestionJobStatus.RUNNING.name())
                .set("leaseOwner", workerId)
                .set("leaseExpiresAt", now.plus(leaseDuration))
                .set("updatedAt", now)
                .inc("attempts", 1);
        IngestionJobDocument claimed = mongoTemplate.findAndModify(
                query,
                update,
                FindAndModifyOptions.options().returnNew(true),
                IngestionJobDocument.class
        );
        return Optional.ofNullable(claimed).map(this::toEntity);
    }

    @Override
    public void deleteByDocumentId(DocumentId documentId) {
        mongoTemplate.remove(new Query(Criteria.where("documentId").is(documentId.value())), IngestionJobDocument.class);
    }

    @Override
    public void deleteByKnowledgeBaseId(KnowledgeBaseId knowledgeBaseId) {
        mongoTemplate.remove(new Query(Criteria.where("knowledgeBaseId").is(knowledgeBaseId.value())), IngestionJobDocument.class);
    }

    private IngestionJobDocument toDocument(IngestionJob job) {
        IngestionJobDocument doc = new IngestionJobDocument();
        doc.setId(job.getId());
        doc.setDocumentId(job.getDocumentId().value());
        doc.setKnowledgeBaseId(job.getKnowledgeBaseId().value());
        doc.setContentObjectId(job.getContentObjectId());
        doc.setStatus(job.getStatus().name());
        doc.setAttempts(job.getAttempts());
        doc.setNextRunAt(job.getNextRunAt());
        doc.setLeaseOwner(job.getLeaseOwner());
        doc.setLeaseExpiresAt(job.getLeaseExpiresAt());
        doc.setFailureReason(job.getFailureReason());
        doc.setCreatedAt(job.getCreatedAt());
        doc.setUpdatedAt(job.getUpdatedAt());
        return doc;
    }

    private IngestionJob toEntity(IngestionJobDocument doc) {
        return IngestionJob.reconstruct(
                doc.getId(),
                new DocumentId(doc.getDocumentId()),
                new KnowledgeBaseId(doc.getKnowledgeBaseId()),
                doc.getContentObjectId(),
                IngestionJobStatus.valueOf(doc.getStatus()),
                doc.getAttempts(),
                doc.getNextRunAt(),
                doc.getLeaseOwner(),
                doc.getLeaseExpiresAt(),
                doc.getFailureReason(),
                doc.getCreatedAt(),
                doc.getUpdatedAt()
        );
    }
}
