package com.synapse.kb;

import com.synapse.kb.model.*;
import com.synapse.kb.port.in.IngestDocumentUseCase;
import com.synapse.kb.port.out.*;
import com.synapse.kb.port.out.RetrievalFilter;
import com.synapse.kb.port.service.DocumentCommandService;
import com.synapse.kb.port.service.support.DocumentIndexingService;
import com.synapse.kb.port.service.support.KnowledgeBaseAccessGuard;
import com.synapse.kb.port.service.support.MetadataSnapshotter;
import com.synapse.kb.repository.DocumentRepository;
import com.synapse.kb.repository.KnowledgeBaseRepository;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class DocumentCommandServiceTests {

    @Test
    void ingestStoresContentAndQueuesJob() {
        Fixture fixture = new Fixture();

        DocumentId id = fixture.service.ingest(new IngestDocumentUseCase.IngestDocumentCommand(
                fixture.kb.getId(), "a.txt", "text/plain", 3, "hash",
                new ByteArrayInputStream("abc".getBytes())
        ));

        Document document = fixture.documents.findById(id).orElseThrow();
        assertEquals(DocumentStatus.PENDING, document.getStatus());
        assertNotNull(document.getContentObjectId());
        assertEquals(1, fixture.contentStore.stored.size());
        assertEquals(1, fixture.jobs.jobs.size());
    }

    @Test
    void ingestRollsBackDocumentAndContentWhenQueueingJobFails() {
        Fixture fixture = new Fixture();
        fixture.jobs.failOnSave = true;

        assertThrows(RuntimeException.class, () -> fixture.service.ingest(new IngestDocumentUseCase.IngestDocumentCommand(
                fixture.kb.getId(), "a.txt", "text/plain", 3, "hash",
                new ByteArrayInputStream("abc".getBytes())
        )));

        assertTrue(fixture.documents.docs.isEmpty());
        assertTrue(fixture.contentStore.stored.isEmpty());
    }

    @Test
    void retryFailedDocumentCleansIndexesAndQueuesJob() {
        Fixture fixture = new Fixture();
        Document document = Document.create(fixture.kb.getId(), "a.txt", "text/plain", 3, "hash");
        document.attachContentObject("content-1");
        document.transitionTo(DocumentStatus.PROCESSING);
        document.transitionTo(DocumentStatus.FAILED, "boom");
        document = fixture.documents.save(document);

        Document retried = fixture.service.retry(document.getId());

        assertEquals(DocumentStatus.PENDING, retried.getStatus());
        assertEquals(1, fixture.vectorDeletes);
        assertEquals(1, fixture.keywordDeletes);
        assertEquals(1, fixture.jobs.jobs.size());
    }

    private static class Fixture {
        private final KnowledgeBase kb = KnowledgeBase.create("kb", "", "user-1");
        private final InMemoryKnowledgeBaseRepository knowledgeBases = new InMemoryKnowledgeBaseRepository(kb);
        private final InMemoryDocumentRepository documents = new InMemoryDocumentRepository();
        private final InMemoryContentStore contentStore = new InMemoryContentStore();
        private final InMemoryJobRepository jobs = new InMemoryJobRepository();
        private int vectorDeletes;
        private int keywordDeletes;
        private final DocumentCommandService service;

        Fixture() {
            KnowledgeBaseAccessGuard accessGuard = new KnowledgeBaseAccessGuard(knowledgeBases, new AllowAllAccessControlPort());
            DocumentIndexingService indexingService = new DocumentIndexingService(
                    new NoopEmbeddingPort(),
                    new VectorStorePort() {
                        @Override public void store(KnowledgeBaseId knowledgeBaseId, DocumentId documentId, String documentName, List<DocumentChunk> chunks, List<float[]> embeddings, DocumentMetadata metadata) {}
                        @Override public List<ChunkReference> search(KnowledgeBaseId knowledgeBaseId, float[] queryEmbedding, int topK, RetrievalFilter filter) { return List.of(); }
                        @Override public void deleteByDocumentId(KnowledgeBaseId knowledgeBaseId, DocumentId documentId) { vectorDeletes++; }
                        @Override public void updateDocumentMetadata(KnowledgeBaseId knowledgeBaseId, DocumentId documentId, DocumentMetadata metadata) {}
                    },
                    new ChunkSearchIndexPort() {
                        @Override public void store(KnowledgeBaseId knowledgeBaseId, DocumentId documentId, String documentName, List<DocumentChunk> chunks, DocumentMetadata metadata) {}
                        @Override public void refreshStore(KnowledgeBaseId knowledgeBaseId, DocumentId documentId, String documentName, List<DocumentChunk> chunks, DocumentMetadata metadata) {}
                        @Override public List<ChunkReference> search(KnowledgeBaseId knowledgeBaseId, String query, int topK, RetrievalFilter filter) { return List.of(); }
                        @Override public void deleteByDocumentId(KnowledgeBaseId knowledgeBaseId, DocumentId documentId) { keywordDeletes++; }
                        @Override public void updateDocumentMetadata(KnowledgeBaseId knowledgeBaseId, DocumentId documentId, DocumentMetadata metadata) {}
                    },
                    new com.synapse.kb.repository.DocumentChunkRepository() {
                        @Override public void save(DocumentId documentId, List<DocumentChunk> chunks) {}
                        @Override public List<DocumentChunk> findByDocumentId(DocumentId documentId) { return List.of(); }
                        @Override public void deleteByDocumentId(DocumentId documentId) {}
                    },
                    contentStore,
                    new MetadataSnapshotter()
            );
            this.service = new DocumentCommandService(
                    accessGuard,
                    documents,
                    new AllowAllAccessControlPort(),
                    contentStore,
                    jobs,
                    indexingService
            );
        }
    }

    private static class InMemoryKnowledgeBaseRepository implements KnowledgeBaseRepository {
        private final KnowledgeBase kb;
        private boolean deleted;
        private InMemoryKnowledgeBaseRepository(KnowledgeBase kb) { this.kb = kb; }
        @Override public KnowledgeBase save(KnowledgeBase knowledgeBase) { return knowledgeBase; }
        @Override public Optional<KnowledgeBase> findById(KnowledgeBaseId id) { return !deleted && kb.getId().equals(id) ? Optional.of(kb) : Optional.empty(); }
        @Override public List<KnowledgeBase> findAll() { return deleted ? List.of() : List.of(kb); }
        @Override public List<KnowledgeBase> findAll(int page, int size) { return findAll(); }
        @Override public void deleteById(KnowledgeBaseId id) { deleted = kb.getId().equals(id); }
    }

    private static class InMemoryDocumentRepository implements DocumentRepository {
        private final Map<DocumentId, Document> docs = new HashMap<>();
        @Override public Document save(Document document) { docs.put(document.getId(), document); return document; }
        @Override public Optional<Document> findById(DocumentId id) { return Optional.ofNullable(docs.get(id)); }
        @Override public List<Document> findByKnowledgeBaseId(KnowledgeBaseId knowledgeBaseId) { return docs.values().stream().filter(doc -> doc.getKnowledgeBaseId().equals(knowledgeBaseId)).toList(); }
        @Override public List<Document> findByKnowledgeBaseId(KnowledgeBaseId knowledgeBaseId, int page, int size) { return findByKnowledgeBaseId(knowledgeBaseId); }
        @Override public void deleteById(DocumentId id) { docs.remove(id); }
        @Override public boolean existsByKnowledgeBaseIdAndContentHash(KnowledgeBaseId knowledgeBaseId, String contentHash) { return false; }
        @Override public List<Document> findByKnowledgeBaseIdAndContentHash(KnowledgeBaseId knowledgeBaseId, String contentHash) { return List.of(); }
        @Override public List<Document> findByKnowledgeBaseIdAndCanonicalKeyAndLifecycleStatus(KnowledgeBaseId knowledgeBaseId, String canonicalKey, DocumentLifecycleStatus status) { return List.of(); }
        @Override public List<Document> findBySupersedesDocumentId(DocumentId documentId) { return docs.values().stream().filter(doc -> documentId.value().equals(doc.getSupersedesDocumentId())).toList(); }
        @Override public List<Document> findByCriteria(com.synapse.kb.repository.DocumentQueryCriteria criteria) { return List.of(); }
        @Override public long countByCriteria(com.synapse.kb.repository.DocumentQueryCriteria criteria) { return 0L; }
    }

    private static class InMemoryContentStore implements DocumentContentStorePort {
        private final Map<String, byte[]> stored = new HashMap<>();
        @Override public String store(KnowledgeBaseId knowledgeBaseId, DocumentId documentId, String fileName, String contentType, InputStream content) {
            String id = "content-" + stored.size();
            try { stored.put(id, content.readAllBytes()); } catch (Exception e) { throw new RuntimeException(e); }
            return id;
        }
        @Override public InputStream open(String contentObjectId) { return new ByteArrayInputStream(stored.getOrDefault(contentObjectId, new byte[0])); }
        @Override public void delete(String contentObjectId) { stored.remove(contentObjectId); }
    }

    private static class InMemoryJobRepository implements IngestionJobRepository {
        private final List<IngestionJob> jobs = new ArrayList<>();
        private boolean failOnSave;
        @Override public IngestionJob save(IngestionJob job) {
            if (failOnSave) throw new RuntimeException("job save failed");
            jobs.removeIf(existing -> existing.getId().equals(job.getId()));
            jobs.add(job);
            return job;
        }
        @Override public Optional<IngestionJob> findLatestByDocumentId(DocumentId documentId) { return jobs.stream().filter(job -> job.getDocumentId().equals(documentId)).findFirst(); }
        @Override public Optional<IngestionJob> claimNext(String workerId, java.time.Duration leaseDuration) {
            return jobs.stream()
                    .filter(job -> job.getStatus() == IngestionJobStatus.QUEUED || job.getStatus() == IngestionJobStatus.RETRYING)
                    .findFirst()
                    .map(job -> {
                        IngestionJob claimed = IngestionJob.reconstruct(
                                job.getId(), job.getDocumentId(), job.getKnowledgeBaseId(), job.getContentObjectId(),
                                IngestionJobStatus.RUNNING, job.getAttempts() + 1, job.getNextRunAt(), workerId,
                                java.time.Instant.now().plus(leaseDuration), job.getFailureReason(), job.getCreatedAt(), java.time.Instant.now()
                        );
                        jobs.remove(job);
                        jobs.add(claimed);
                        return claimed;
                    });
        }
        @Override public void deleteByDocumentId(DocumentId documentId) { jobs.removeIf(job -> job.getDocumentId().equals(documentId)); }
        @Override public void deleteByKnowledgeBaseId(KnowledgeBaseId knowledgeBaseId) { jobs.removeIf(job -> job.getKnowledgeBaseId().equals(knowledgeBaseId)); }
    }

    private static class AllowAllAccessControlPort implements AccessControlPort {
        @Override public String currentUserId() { return "user-1"; }
        @Override public void checkPermission(String permission) {}
        @Override public void checkKnowledgeBaseAccess(KnowledgeBase knowledgeBase, String permission) {}
        @Override public boolean isAdmin() { return false; }
    }

    private static class NoopEmbeddingPort implements EmbeddingPort {
        @Override public float[] embed(String text) { return new float[]{1}; }
        @Override public List<float[]> embed(List<String> texts) { return texts.stream().map(text -> new float[]{1}).toList(); }
    }
}
