package com.synapse.kb;

import com.synapse.kb.model.*;
import com.synapse.kb.port.out.*;
import com.synapse.kb.port.out.RetrievalFilter;
import com.synapse.kb.port.service.KnowledgeBaseManagementService;
import com.synapse.kb.port.service.support.DocumentIndexingService;
import com.synapse.kb.port.service.support.KnowledgeBaseAccessGuard;
import com.synapse.kb.port.service.support.MetadataSnapshotter;
import com.synapse.kb.repository.DocumentChunkRepository;
import com.synapse.kb.repository.DocumentRepository;
import com.synapse.kb.repository.KnowledgeBaseRepository;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class KnowledgeBaseManagementServiceTests {

    @Test
    void deleteKnowledgeBaseCascadesDocumentsIndexesContentAndJobs() {
        KnowledgeBase kb = KnowledgeBase.create("kb", "", "user-1");
        InMemoryKnowledgeBaseRepository knowledgeBases = new InMemoryKnowledgeBaseRepository(kb);
        InMemoryDocumentRepository documents = new InMemoryDocumentRepository();
        InMemoryContentStore contentStore = new InMemoryContentStore();
        InMemoryJobRepository jobs = new InMemoryJobRepository();
        AtomicCounter vectorDeletes = new AtomicCounter();
        AtomicCounter keywordDeletes = new AtomicCounter();

        Document document = Document.create(kb.getId(), "a.txt", "text/plain", 3, "hash");
        String contentObjectId = contentStore.store(
                kb.getId(), document.getId(), "a.txt", "text/plain",
                new ByteArrayInputStream("abc".getBytes())
        );
        document.attachContentObject(contentObjectId);
        documents.save(document);
        jobs.save(IngestionJob.create(document.getId(), kb.getId(), contentObjectId));

        DocumentIndexingService indexingService = new DocumentIndexingService(
                new NoopEmbeddingPort(),
                new VectorStorePort() {
                    @Override public void store(KnowledgeBaseId knowledgeBaseId, DocumentId documentId, String documentName, List<DocumentChunk> chunks, List<float[]> embeddings, DocumentMetadata metadata) {}
                    @Override public List<ChunkReference> search(KnowledgeBaseId knowledgeBaseId, float[] queryEmbedding, int topK, RetrievalFilter filter) { return List.of(); }
                    @Override public void deleteByDocumentId(KnowledgeBaseId knowledgeBaseId, DocumentId documentId) { vectorDeletes.increment(); }
                    @Override public void updateDocumentMetadata(KnowledgeBaseId knowledgeBaseId, DocumentId documentId, DocumentMetadata metadata) {}
                },
                new ChunkSearchIndexPort() {
                    @Override public void store(KnowledgeBaseId knowledgeBaseId, DocumentId documentId, String documentName, List<DocumentChunk> chunks, DocumentMetadata metadata) {}
                    @Override public void refreshStore(KnowledgeBaseId knowledgeBaseId, DocumentId documentId, String documentName, List<DocumentChunk> chunks, DocumentMetadata metadata) {}
                    @Override public List<ChunkReference> search(KnowledgeBaseId knowledgeBaseId, String query, int topK, RetrievalFilter filter) { return List.of(); }
                    @Override public void deleteByDocumentId(KnowledgeBaseId knowledgeBaseId, DocumentId documentId) { keywordDeletes.increment(); }
                    @Override public void updateDocumentMetadata(KnowledgeBaseId knowledgeBaseId, DocumentId documentId, DocumentMetadata metadata) {}
                },
                new DocumentChunkRepository() {
                    @Override public void save(DocumentId documentId, List<DocumentChunk> chunks) {}
                    @Override public List<DocumentChunk> findByDocumentId(DocumentId documentId) { return List.of(); }
                    @Override public void deleteByDocumentId(DocumentId documentId) {}
                },
                contentStore,
                new MetadataSnapshotter()
        );

        AllowAllAccessControlPort accessControl = new AllowAllAccessControlPort();
        KnowledgeBaseAccessGuard accessGuard = new KnowledgeBaseAccessGuard(knowledgeBases, accessControl);

        KnowledgeBaseManagementService service = new KnowledgeBaseManagementService(
                accessGuard,
                knowledgeBases,
                accessControl,
                documents,
                indexingService,
                new DocumentChunkRepository() {
                    @Override public void save(DocumentId documentId, List<DocumentChunk> chunks) {}
                    @Override public List<DocumentChunk> findByDocumentId(DocumentId documentId) { return List.of(); }
                    @Override public void deleteByDocumentId(DocumentId documentId) {}
                },
                new com.synapse.kb.port.out.DocumentIndexRefreshJobRepository() {
                    @Override public com.synapse.kb.model.DocumentIndexRefreshJob save(com.synapse.kb.model.DocumentIndexRefreshJob job) { return job; }
                    @Override public java.util.Optional<com.synapse.kb.model.DocumentIndexRefreshJob> findById(String id) { return java.util.Optional.empty(); }
                    @Override public List<com.synapse.kb.model.DocumentIndexRefreshJob> findPendingJobs(java.time.Instant before, int limit) { return List.of(); }
                    @Override public java.util.Optional<com.synapse.kb.model.DocumentIndexRefreshJob> claimNext(String workerId) { return java.util.Optional.empty(); }
                    @Override public void deleteByDocumentId(String documentId) {}
                },
                jobs
        );

        service.delete(kb.getId());

        assertTrue(documents.docs.isEmpty());
        assertTrue(contentStore.stored.isEmpty());
        assertTrue(jobs.jobs.isEmpty());
        assertEquals(1, vectorDeletes.value);
        assertEquals(1, keywordDeletes.value);
        assertTrue(knowledgeBases.deleted);
    }

    private static class AtomicCounter {
        int value;
        void increment() { value++; }
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
        @Override public IngestionJob save(IngestionJob job) {
            jobs.removeIf(existing -> existing.getId().equals(job.getId()));
            jobs.add(job);
            return job;
        }
        @Override public Optional<IngestionJob> findLatestByDocumentId(DocumentId documentId) { return jobs.stream().filter(job -> job.getDocumentId().equals(documentId)).findFirst(); }
        @Override public Optional<IngestionJob> claimNext(String workerId, java.time.Duration leaseDuration) { return Optional.empty(); }
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
