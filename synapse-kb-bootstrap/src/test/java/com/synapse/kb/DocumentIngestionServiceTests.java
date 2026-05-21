package com.synapse.kb;

import com.synapse.kb.model.*;
import com.synapse.kb.port.out.*;
import com.synapse.kb.port.out.RetrievalFilter;
import com.synapse.kb.port.service.DocumentIngestionService;
import com.synapse.kb.port.service.support.DocumentIndexingService;
import com.synapse.kb.port.service.support.FailureReasonSanitizer;
import com.synapse.kb.port.service.support.MetadataSnapshotter;
import com.synapse.kb.repository.DocumentRepository;
import com.synapse.kb.service.RecursiveChunkingStrategy;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Duration;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class DocumentIngestionServiceTests {

    @Test
    void processNextAvailableCompletesQueuedDocument() {
        Fixture fixture = new Fixture();
        DocumentId id = fixture.createPendingDocument("hello world");
        fixture.jobs.save(IngestionJob.create(id, fixture.kb.getId(), fixture.documents.findById(id).orElseThrow().getContentObjectId()));

        assertTrue(fixture.service.processNextAvailable("worker-1"));

        Document document = fixture.documents.findById(id).orElseThrow();
        assertEquals(DocumentStatus.COMPLETED, document.getStatus());
        assertEquals(1, document.getChunkCount());
        assertEquals(1, fixture.vectorStores);
        assertEquals(1, fixture.keywordStores);
        assertEquals(IngestionJobStatus.SUCCEEDED, fixture.jobs.jobs.getFirst().getStatus());
    }

    private static class Fixture {
        private final KnowledgeBase kb = KnowledgeBase.create("kb", "", "user-1");
        private final InMemoryDocumentRepository documents = new InMemoryDocumentRepository();
        private final InMemoryContentStore contentStore = new InMemoryContentStore();
        private final InMemoryJobRepository jobs = new InMemoryJobRepository();
        private int vectorStores;
        private int keywordStores;
        private final DocumentIngestionService service;

        Fixture() {
            DocumentIndexingService indexingService = new DocumentIndexingService(
                    new NoopEmbeddingPort(),
                    new VectorStorePort() {
                        @Override public void store(KnowledgeBaseId knowledgeBaseId, DocumentId documentId, String documentName, List<DocumentChunk> chunks, List<float[]> embeddings, DocumentMetadata metadata) { vectorStores++; }
                        @Override public List<ChunkReference> search(KnowledgeBaseId knowledgeBaseId, float[] queryEmbedding, int topK, RetrievalFilter filter) { return List.of(); }
                        @Override public void deleteByDocumentId(KnowledgeBaseId knowledgeBaseId, DocumentId documentId) {}
                        @Override public void updateDocumentMetadata(KnowledgeBaseId knowledgeBaseId, DocumentId documentId, DocumentMetadata metadata) {}
                    },
                    new ChunkSearchIndexPort() {
                        @Override public void store(KnowledgeBaseId knowledgeBaseId, DocumentId documentId, String documentName, List<DocumentChunk> chunks, DocumentMetadata metadata) { keywordStores++; }
                        @Override public void refreshStore(KnowledgeBaseId knowledgeBaseId, DocumentId documentId, String documentName, List<DocumentChunk> chunks, DocumentMetadata metadata) { keywordStores++; }
                        @Override public List<ChunkReference> search(KnowledgeBaseId knowledgeBaseId, String query, int topK, RetrievalFilter filter) { return List.of(); }
                        @Override public void deleteByDocumentId(KnowledgeBaseId knowledgeBaseId, DocumentId documentId) {}
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
            this.service = new DocumentIngestionService(
                    documents,
                    (inputStream, fileName) -> {
                        try { return new String(inputStream.readAllBytes()); } catch (Exception e) { throw new RuntimeException(e); }
                    },
                    new RecursiveChunkingStrategy(1000, 0.15, 80, 200),
                    contentStore,
                    indexingService,
                    jobs,
                    new FailureReasonSanitizer(),
                    3,
                    Duration.ofMinutes(5)
            );
        }

        DocumentId createPendingDocument(String content) {
            Document document = Document.create(kb.getId(), "a.txt", "text/plain", content.length(), "hash");
            String contentObjectId = contentStore.store(kb.getId(), document.getId(), "a.txt", "text/plain", new ByteArrayInputStream(content.getBytes()));
            document.attachContentObject(contentObjectId);
            return documents.save(document).getId();
        }
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
        @Override public IngestionJob save(IngestionJob job) {
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

    private static class NoopEmbeddingPort implements EmbeddingPort {
        @Override public float[] embed(String text) { return new float[]{1}; }
        @Override public List<float[]> embed(List<String> texts) { return texts.stream().map(text -> new float[]{1}).toList(); }
    }
}
