package com.synapse.kb;

import com.synapse.kb.model.*;
import com.synapse.kb.port.in.IngestDocumentUseCase;
import com.synapse.kb.port.out.*;
import com.synapse.kb.port.service.KnowledgeBaseApplicationService;
import com.synapse.kb.repository.ChatMessageRepository;
import com.synapse.kb.repository.ChatSessionRepository;
import com.synapse.kb.repository.DocumentRepository;
import com.synapse.kb.repository.KnowledgeBaseRepository;
import com.synapse.kb.service.RecursiveChunkingStrategy;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;

class KnowledgeBaseApplicationServiceIngestionTests {

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
    void processNextAvailableCompletesQueuedDocument() {
        Fixture fixture = new Fixture();
        DocumentId id = fixture.service.ingest(new IngestDocumentUseCase.IngestDocumentCommand(
                fixture.kb.getId(), "a.txt", "text/plain", 11, "hash",
                new ByteArrayInputStream("hello world".getBytes())
        ));

        assertTrue(fixture.service.processNextAvailable("worker-1"));

        Document document = fixture.documents.findById(id).orElseThrow();
        assertEquals(DocumentStatus.COMPLETED, document.getStatus());
        assertEquals(1, document.getChunkCount());
        assertEquals(1, fixture.vectorStores);
        assertEquals(1, fixture.keywordStores);
        assertEquals(IngestionJobStatus.SUCCEEDED, fixture.jobs.jobs.getFirst().getStatus());
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

    @Test
    void deleteKnowledgeBaseCascadesDocumentsIndexesContentAndJobs() {
        Fixture fixture = new Fixture();
        Document document = Document.create(fixture.kb.getId(), "a.txt", "text/plain", 3, "hash");
        String contentObjectId = fixture.contentStore.store(
                fixture.kb.getId(),
                document.getId(),
                "a.txt",
                "text/plain",
                new ByteArrayInputStream("abc".getBytes())
        );
        document.attachContentObject(contentObjectId);
        fixture.documents.save(document);
        fixture.jobs.save(IngestionJob.create(document.getId(), fixture.kb.getId(), contentObjectId));

        fixture.service.delete(fixture.kb.getId());

        assertTrue(fixture.documents.docs.isEmpty());
        assertTrue(fixture.contentStore.stored.isEmpty());
        assertTrue(fixture.jobs.jobs.isEmpty());
        assertEquals(1, fixture.vectorDeletes);
        assertEquals(1, fixture.keywordDeletes);
        assertTrue(fixture.knowledgeBases.deleted);
    }

    private static class Fixture {
        private final KnowledgeBase kb = KnowledgeBase.create("kb", "", "user-1");
        private final InMemoryKnowledgeBaseRepository knowledgeBases = new InMemoryKnowledgeBaseRepository(kb);
        private final InMemoryDocumentRepository documents = new InMemoryDocumentRepository();
        private final InMemoryContentStore contentStore = new InMemoryContentStore();
        private final InMemoryJobRepository jobs = new InMemoryJobRepository();
        private int vectorDeletes;
        private int keywordDeletes;
        private int vectorStores;
        private int keywordStores;
        private final KnowledgeBaseApplicationService service = new KnowledgeBaseApplicationService(
                knowledgeBases,
                documents,
                new EmptyChatSessionRepository(),
                new EmptyChatMessageRepository(),
                (inputStream, fileName) -> {
                    try {
                        return new String(inputStream.readAllBytes());
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                },
                new RecursiveChunkingStrategy(1000, 0.15, 80, 200),
                contentStore,
                jobs,
                new NoopEmbeddingPort(),
                new VectorStorePort() {
                    @Override public void store(KnowledgeBaseId knowledgeBaseId, DocumentId documentId, String documentName, List<DocumentChunk> chunks, List<float[]> embeddings) { vectorStores++; }
                    @Override public List<ChunkReference> search(KnowledgeBaseId knowledgeBaseId, float[] queryEmbedding, int topK) { return List.of(); }
                    @Override public void deleteByDocumentId(KnowledgeBaseId knowledgeBaseId, DocumentId documentId) { vectorDeletes++; }
                },
                new ChunkSearchIndexPort() {
                    @Override public void store(KnowledgeBaseId knowledgeBaseId, DocumentId documentId, String documentName, List<DocumentChunk> chunks) { keywordStores++; }
                    @Override public List<ChunkReference> search(KnowledgeBaseId knowledgeBaseId, String query, int topK) { return List.of(); }
                    @Override public void deleteByDocumentId(KnowledgeBaseId knowledgeBaseId, DocumentId documentId) { keywordDeletes++; }
                },
                query -> query,
                (existingSummary, messages, maxChars) -> existingSummary,
                new AllowAllAccessControlPort(),
                Runnable::run,
                "%s%s",
                5, 20, 20, 0.65, 0.35,
                false, 0.8, false, 8, 12, 1500, 3, Duration.ofMinutes(5)
        );
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
    }

    private static class InMemoryContentStore implements DocumentContentStorePort {
        private final Map<String, byte[]> stored = new HashMap<>();
        @Override public String store(KnowledgeBaseId knowledgeBaseId, DocumentId documentId, String fileName, String contentType, InputStream content) {
            String id = "content-" + stored.size();
            try {
                stored.put(id, content.readAllBytes());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return id;
        }
        @Override public InputStream open(String contentObjectId) { return new ByteArrayInputStream(stored.getOrDefault(contentObjectId, new byte[0])); }
        @Override public void delete(String contentObjectId) { stored.remove(contentObjectId); }
    }

    private static class InMemoryJobRepository implements IngestionJobRepository {
        private final List<IngestionJob> jobs = new ArrayList<>();
        private boolean failOnSave;
        @Override public IngestionJob save(IngestionJob job) {
            if (failOnSave) {
                throw new RuntimeException("job save failed");
            }
            jobs.removeIf(existing -> existing.getId().equals(job.getId()));
            jobs.add(job);
            return job;
        }
        @Override public Optional<IngestionJob> findLatestByDocumentId(DocumentId documentId) { return jobs.stream().filter(job -> job.getDocumentId().equals(documentId)).findFirst(); }
        @Override public Optional<IngestionJob> claimNext(String workerId, Duration leaseDuration) {
            return jobs.stream()
                    .filter(job -> job.getStatus() == IngestionJobStatus.QUEUED || job.getStatus() == IngestionJobStatus.RETRYING)
                    .findFirst()
                    .map(job -> {
                        IngestionJob claimed = IngestionJob.reconstruct(
                                job.getId(),
                                job.getDocumentId(),
                                job.getKnowledgeBaseId(),
                                job.getContentObjectId(),
                                IngestionJobStatus.RUNNING,
                                job.getAttempts() + 1,
                                job.getNextRunAt(),
                                workerId,
                                java.time.Instant.now().plus(leaseDuration),
                                job.getFailureReason(),
                                job.getCreatedAt(),
                                java.time.Instant.now()
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

    private static class EmptyChatSessionRepository implements ChatSessionRepository {
        @Override public ChatSession save(ChatSession session) { return session; }
        @Override public long nextMessageSequence(ChatSessionId id) { return 1; }
        @Override public Optional<ChatSession> findById(ChatSessionId id) { return Optional.empty(); }
        @Override public Optional<ChatSession> findLatestByOwnerUserIdAndKnowledgeBaseId(String ownerUserId, KnowledgeBaseId knowledgeBaseId) { return Optional.empty(); }
    }

    private static class EmptyChatMessageRepository implements ChatMessageRepository {
        @Override public ChatMessage save(ChatMessage message) { return message; }
        @Override public List<ChatMessage> findBySessionId(ChatSessionId sessionId, int page, int size) { return List.of(); }
        @Override public List<ChatMessage> findRecentBySessionIdBeforeOrEqual(ChatSessionId sessionId, long maxSequence, int limit) { return List.of(); }
        @Override public List<ChatMessage> findBySessionIdAndSequenceBetween(ChatSessionId sessionId, long fromExclusive, long toInclusive) { return List.of(); }
    }
}
