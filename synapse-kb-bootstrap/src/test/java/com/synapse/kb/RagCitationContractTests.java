package com.synapse.kb;

import com.synapse.kb.model.*;
import com.synapse.kb.port.in.QueryKnowledgeBaseUseCase;
import com.synapse.kb.port.out.*;
import com.synapse.kb.port.out.RetrievalFilter;
import com.synapse.kb.port.service.QueryKnowledgeBaseApplicationService;
import com.synapse.kb.port.service.ChatApplicationService;
import com.synapse.kb.port.service.support.*;
import com.synapse.kb.repository.*;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDate;
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
        QueryKnowledgeBaseUseCase service = newService(kb, vectorResults);

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

    @Test
    void prepareUsesResolvedAsOfDateForMongoAuthorityFallbackWhenRequestDateIsAbsent() {
        KnowledgeBase kb = KnowledgeBase.create("kb", "", "user-1");
        Document document = Document.reconstruct(
                new DocumentId("doc-1"),
                kb.getId(),
                "法规.pdf",
                "application/pdf",
                100,
                "hash-1",
                Instant.parse("2024-01-01T00:00:00Z"),
                DocumentStatus.COMPLETED,
                null,
                1,
                null,
                null,
                Instant.parse("2024-01-01T00:01:00Z"),
                DocumentSourceType.LEGAL,
                "law-x",
                "2024版",
                LocalDate.of(2024, 1, 1),
                null,
                DocumentLifecycleStatus.ACTIVE,
                null,
                10,
                "全国",
                0L,
                0L,
                DocumentIndexStatus.SYNCED,
                Instant.parse("2024-01-01T00:02:00Z"),
                null
        );
        List<ChunkReference> vectorResults = List.of(
                new ChunkReference("doc-1", "法规.pdf", 0, "现行有效条款。", 0.92f, 0, 8)
        );
        QueryKnowledgeBaseUseCase service = newService(kb, vectorResults, new MapDocumentRepository(document));

        RagContext context = service.prepare(new Query(
                kb.getId(),
                "查询法规条款",
                null,
                null,
                DocumentSourceType.LEGAL,
                "全国"
        ));

        assertEquals(1, context.references().size());
        assertEquals("doc-1", context.references().getFirst().documentId());
        assertTrue(context.prompt().contains("现行有效条款。"));
    }

    private QueryKnowledgeBaseUseCase newService(KnowledgeBase kb, List<ChunkReference> vectorResults) {
        return newService(kb, vectorResults, new EmptyDocumentRepository());
    }

    private QueryKnowledgeBaseUseCase newService(KnowledgeBase kb, List<ChunkReference> vectorResults,
                                                   DocumentRepository documentRepository) {
        AccessControlPort accessControl = new AllowAllAccessControlPort();
        KnowledgeBaseRepository kbRepo = new SingleKnowledgeBaseRepository(kb);
        KnowledgeBaseAccessGuard accessGuard = new KnowledgeBaseAccessGuard(kbRepo, accessControl);

        ChatApplicationService chatService = new ChatApplicationService(
                accessGuard,
                new EmptyChatSessionRepository(),
                new EmptyChatMessageRepository(),
                accessControl,
                (existingSummary, messages, maxChars) -> existingSummary,
                8, 12, 1500
        );

        QueryPreparationService queryPrep = new QueryPreparationService(
                new NoopEmbeddingPort(),
                query -> query,
                false, 0.8
        );

        HybridRetrievalService hybrid = new HybridRetrievalService(
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
                Runnable::run,
                5, 20, 20, 0.65, 0.35
        );

        PromptContextBuilder promptBuilder = new PromptContextBuilder();

        return new QueryKnowledgeBaseApplicationService(
                accessGuard,
                documentRepository,
                chatService,
                queryPrep,
                hybrid,
                promptBuilder,
                accessControl,
                "%s\nQUESTION:%s",
                false
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
        @Override public List<Document> findByCriteria(DocumentQueryCriteria criteria) { return List.of(); }
        @Override public long countByCriteria(DocumentQueryCriteria criteria) { return 0L; }
    }

    private static class MapDocumentRepository extends EmptyDocumentRepository {
        private final Map<DocumentId, Document> documents = new HashMap<>();
        private MapDocumentRepository(Document... documents) {
            for (Document document : documents) {
                this.documents.put(document.getId(), document);
            }
        }
        @Override public Optional<Document> findById(DocumentId id) { return Optional.ofNullable(documents.get(id)); }
        @Override public List<Document> findByKnowledgeBaseId(KnowledgeBaseId knowledgeBaseId) {
            return documents.values().stream()
                    .filter(document -> document.getKnowledgeBaseId().equals(knowledgeBaseId))
                    .toList();
        }
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
