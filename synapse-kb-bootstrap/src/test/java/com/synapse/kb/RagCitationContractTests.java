package com.synapse.kb;

import com.synapse.kb.model.*;
import com.synapse.kb.port.out.*;
import com.synapse.kb.port.out.RetrievalFilter;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class RagCitationContractTests {

    @Test
    void prepareFormatsSourcesWithStableIdsAndMetadata() {
        KnowledgeBase kb = KnowledgeBase.create("kb", "", "user-1");
        List<ChunkReference> vectorResults = List.of(
                new ChunkReference("doc-1", "阿里手册.pdf", 3, "异常不要用来做流程控制。", 0.92f, 10, 30),
                new ChunkReference("doc-2", "Java规范.pdf", 7, "finally 块中不要使用 return。", 0.81f, 40, 70)
        );
        KnowledgeBaseApplicationService service = newService(kb, vectorResults);

        RagContext context = service.prepare(new Query(kb.getId(), "Java 中异常有什么规范", null));

        assertEquals("doc-1", context.references().get(0).documentId());
        assertEquals("doc-2", context.references().get(1).documentId());
        assertTrue(context.prompt().contains("<source id=\"1\">"));
        assertTrue(context.prompt().contains("<documentName>阿里手册.pdf</documentName>"));
        assertTrue(context.prompt().contains("<chunkIndex>3</chunkIndex>"));
        assertTrue(context.prompt().contains("<score>0.5980</score>"));
        assertTrue(context.prompt().contains("异常不要用来做流程控制。"));
        assertTrue(context.prompt().contains("<source id=\"2\">"));
        assertTrue(context.prompt().contains("finally 块中不要使用 return。"));
    }

    private KnowledgeBaseApplicationService newService(KnowledgeBase kb, List<ChunkReference> vectorResults) {
        return new KnowledgeBaseApplicationService(
                new SingleKnowledgeBaseRepository(kb),
                new EmptyDocumentRepository(),
                new EmptyChatSessionRepository(),
                new EmptyChatMessageRepository(),
                (inputStream, fileName) -> "",
                new RecursiveChunkingStrategy(1000, 0.15, 80, 200),
                new InMemoryContentStore(),
                new NoopIngestionJobRepository(),
                new NoopEmbeddingPort(),
                new VectorStorePort() {
                    @Override public void store(KnowledgeBaseId knowledgeBaseId, DocumentId documentId, String documentName, List<DocumentChunk> chunks, List<float[]> embeddings, DocumentMetadata metadata) {}
                    @Override public List<ChunkReference> search(KnowledgeBaseId knowledgeBaseId, float[] queryEmbedding, int topK, RetrievalFilter filter) { return vectorResults; }
                    @Override public void deleteByDocumentId(KnowledgeBaseId knowledgeBaseId, DocumentId documentId) {}
                    @Override public void updateDocumentMetadata(KnowledgeBaseId knowledgeBaseId, DocumentId documentId, DocumentMetadata metadata) {}
                },
                new ChunkSearchIndexPort() {
                    @Override public void store(KnowledgeBaseId knowledgeBaseId, DocumentId documentId, String documentName, List<DocumentChunk> chunks, DocumentMetadata metadata) {}
                    @Override public void refreshStore(KnowledgeBaseId knowledgeBaseId, DocumentId documentId, String documentName, List<DocumentChunk> chunks, DocumentMetadata metadata) {}
                    @Override public List<ChunkReference> search(KnowledgeBaseId knowledgeBaseId, String query, int topK, RetrievalFilter filter) { return List.of(); }
                    @Override public void deleteByDocumentId(KnowledgeBaseId knowledgeBaseId, DocumentId documentId) {}
                    @Override public void updateDocumentMetadata(KnowledgeBaseId knowledgeBaseId, DocumentId documentId, DocumentMetadata metadata) {}
                },
                new com.synapse.kb.repository.DocumentChunkRepository() {
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
                query -> query,
                (existingSummary, messages, maxChars) -> existingSummary,
                new com.synapse.kb.port.out.AuditEventStorePort() {
                    @Override public void save(com.synapse.kb.port.out.AuditEventStorePort.AuditEvent event) {}
                    @Override public List<com.synapse.kb.port.out.AuditEventStorePort.AuditEvent> findByDocumentId(DocumentId documentId) { return List.of(); }
                },
                new AllowAllAccessControlPort(),
                Runnable::run,
                "%s\nQUESTION:%s",
                5, 20, 20, 0.65, 0.35,
                false, 0.8, false, 8, 12, 1500, 3, Duration.ofMinutes(5)
        );
    }

    private static class SingleKnowledgeBaseRepository implements KnowledgeBaseRepository {
        private final KnowledgeBase kb;
        private SingleKnowledgeBaseRepository(KnowledgeBase kb) { this.kb = kb; }
        @Override public KnowledgeBase save(KnowledgeBase knowledgeBase) { return knowledgeBase; }
        @Override public Optional<KnowledgeBase> findById(KnowledgeBaseId id) { return kb.getId().equals(id) ? Optional.of(kb) : Optional.empty(); }
        @Override public List<KnowledgeBase> findAll() { return List.of(kb); }
        @Override public List<KnowledgeBase> findAll(int page, int size) { return List.of(kb); }
        @Override public void deleteById(KnowledgeBaseId id) {}
    }

    private static class EmptyDocumentRepository implements DocumentRepository {
        @Override public Document save(Document document) { return document; }
        @Override public Optional<Document> findById(DocumentId id) { return Optional.empty(); }
        @Override public List<Document> findByKnowledgeBaseId(KnowledgeBaseId knowledgeBaseId) { return List.of(); }
        @Override public List<Document> findByKnowledgeBaseId(KnowledgeBaseId knowledgeBaseId, int page, int size) { return List.of(); }
        @Override public void deleteById(DocumentId id) {}
        @Override public boolean existsByKnowledgeBaseIdAndContentHash(KnowledgeBaseId knowledgeBaseId, String contentHash) { return false; }
        @Override public List<Document> findByKnowledgeBaseIdAndContentHash(KnowledgeBaseId knowledgeBaseId, String contentHash) { return List.of(); }
        @Override public List<Document> findByKnowledgeBaseIdAndCanonicalKeyAndLifecycleStatus(KnowledgeBaseId knowledgeBaseId, String canonicalKey, DocumentLifecycleStatus status) { return List.of(); }
        @Override public List<Document> findBySupersedesDocumentId(DocumentId documentId) { return List.of(); }
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

    private static class InMemoryContentStore implements DocumentContentStorePort {
        private final Map<String, byte[]> stored = new HashMap<>();
        @Override public String store(KnowledgeBaseId knowledgeBaseId, DocumentId documentId, String fileName, String contentType, InputStream content) {
            String id = "content-" + stored.size();
            stored.put(id, new byte[0]);
            return id;
        }
        @Override public InputStream open(String contentObjectId) { return new ByteArrayInputStream(stored.getOrDefault(contentObjectId, new byte[0])); }
        @Override public void delete(String contentObjectId) { stored.remove(contentObjectId); }
    }

    private static class NoopIngestionJobRepository implements IngestionJobRepository {
        @Override public IngestionJob save(IngestionJob job) { return job; }
        @Override public Optional<IngestionJob> findLatestByDocumentId(DocumentId documentId) { return Optional.empty(); }
        @Override public Optional<IngestionJob> claimNext(String workerId, Duration leaseDuration) { return Optional.empty(); }
        @Override public void deleteByDocumentId(DocumentId documentId) {}
        @Override public void deleteByKnowledgeBaseId(KnowledgeBaseId knowledgeBaseId) {}
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
