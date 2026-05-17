package com.synapse.kb.port.service;

import com.synapse.kb.model.*;
import com.synapse.kb.port.in.*;
import com.synapse.kb.port.out.*;
import com.synapse.kb.repository.ChatMessageRepository;
import com.synapse.kb.repository.ChatSessionRepository;
import com.synapse.kb.repository.DocumentRepository;
import com.synapse.kb.repository.KnowledgeBaseRepository;
import com.synapse.kb.service.RecursiveChunkingStrategy;
import com.synapse.shared.exception.DomainException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

/**
 * 知识库用例编排器。应用层保持同步 API，外部技术能力全部通过端口注入。
 */
public class KnowledgeBaseApplicationService implements
        CreateKnowledgeBaseUseCase,
        DeleteKnowledgeBaseUseCase,
        ListKnowledgeBaseUseCase,
        IngestDocumentUseCase,
        ListDocumentUseCase,
        DeleteDocumentUseCase,
        QueryKnowledgeBaseUseCase,
        GetCurrentChatSessionUseCase,
        CreateChatSessionUseCase,
        ListChatMessagesUseCase {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseApplicationService.class);
    private static final int MAX_FAILURE_REASON_LENGTH = 500;

    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final DocumentRepository documentRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final DocumentParserPort documentParserPort;
    private final RecursiveChunkingStrategy recursiveChunkingStrategy;
    private final EmbeddingPort embeddingPort;
    private final VectorStorePort vectorStorePort;
    private final ChunkSearchIndexPort chunkSearchIndexPort;
    private final QueryRewritePort queryRewritePort;
    private final ChatMemorySummarizerPort chatMemorySummarizerPort;
    private final AccessControlPort accessControlPort;
    private final Executor ingestionExecutor;
    private final Executor retrievalExecutor;

    /** 模板必须包含两个 {@code %s} 占位符：检索上下文、用户问题。 */
    private final String promptTemplate;
    private final int topK;
    private final int vectorCandidateK;
    private final int keywordCandidateK;
    private final double vectorWeight;
    private final double keywordWeight;
    private final boolean queryRewriteEnabled;
    private final double queryRewriteSimilarityThreshold;
    private final boolean chatMemoryEnabled;
    private final int recentMessageLimit;
    private final int summaryTriggerMessageCount;
    private final int maxSummaryChars;

    public KnowledgeBaseApplicationService(
            KnowledgeBaseRepository knowledgeBaseRepository,
            DocumentRepository documentRepository,
            ChatSessionRepository chatSessionRepository,
            ChatMessageRepository chatMessageRepository,
            DocumentParserPort documentParserPort,
            RecursiveChunkingStrategy recursiveChunkingStrategy,
            EmbeddingPort embeddingPort,
            VectorStorePort vectorStorePort,
            ChunkSearchIndexPort chunkSearchIndexPort,
            QueryRewritePort queryRewritePort,
            ChatMemorySummarizerPort chatMemorySummarizerPort,
            AccessControlPort accessControlPort,
            Executor ingestionExecutor,
            Executor retrievalExecutor,
            String promptTemplate,
            int topK,
            int vectorCandidateK,
            int keywordCandidateK,
            double vectorWeight,
            double keywordWeight,
            boolean queryRewriteEnabled,
            double queryRewriteSimilarityThreshold,
            boolean chatMemoryEnabled,
            int recentMessageLimit,
            int summaryTriggerMessageCount,
            int maxSummaryChars
    ) {
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.documentRepository = documentRepository;
        this.chatSessionRepository = chatSessionRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.documentParserPort = documentParserPort;
        this.recursiveChunkingStrategy = recursiveChunkingStrategy;
        this.embeddingPort = embeddingPort;
        this.vectorStorePort = vectorStorePort;
        this.chunkSearchIndexPort = chunkSearchIndexPort;
        this.queryRewritePort = queryRewritePort;
        this.chatMemorySummarizerPort = chatMemorySummarizerPort;
        this.accessControlPort = accessControlPort;
        this.ingestionExecutor = ingestionExecutor;
        this.retrievalExecutor = retrievalExecutor;
        this.promptTemplate = promptTemplate;
        this.topK = Math.max(1, Math.min(topK, 20));
        this.vectorCandidateK = Math.max(this.topK, Math.min(vectorCandidateK, 100));
        this.keywordCandidateK = Math.max(this.topK, Math.min(keywordCandidateK, 100));
        double safeVectorWeight = Math.max(0, vectorWeight);
        double safeKeywordWeight = Math.max(0, keywordWeight);
        double totalWeight = safeVectorWeight + safeKeywordWeight;
        if (totalWeight == 0) {
            safeVectorWeight = 1;
            totalWeight = 1;
        }
        this.vectorWeight = safeVectorWeight / totalWeight;
        this.keywordWeight = safeKeywordWeight / totalWeight;
        this.queryRewriteEnabled = queryRewriteEnabled;
        this.queryRewriteSimilarityThreshold = Math.max(0, Math.min(queryRewriteSimilarityThreshold, 1));
        this.chatMemoryEnabled = chatMemoryEnabled;
        this.recentMessageLimit = Math.max(0, Math.min(recentMessageLimit, 50));
        this.summaryTriggerMessageCount = Math.max(1, summaryTriggerMessageCount);
        this.maxSummaryChars = Math.max(200, maxSummaryChars);
    }

    @Override
    public KnowledgeBaseId create(CreateKnowledgeBaseCommand command) {
        accessControlPort.checkPermission("KB_WRITE");
        KnowledgeBase kb = KnowledgeBase.create(command.name(), command.description(), accessControlPort.currentUserId());
        return knowledgeBaseRepository.save(kb).getId();
    }

    @Override
    public List<KnowledgeBase> listAll() {
        accessControlPort.checkPermission("KB_READ");
        return filterVisible(knowledgeBaseRepository.findAll());
    }

    @Override
    public List<KnowledgeBase> listAll(int page, int size) {
        accessControlPort.checkPermission("KB_READ");
        return filterVisible(knowledgeBaseRepository.findAll(page, size));
    }

    @Override
    public void delete(KnowledgeBaseId id) {
        KnowledgeBase kb = requireKnowledgeBase(id);
        accessControlPort.checkKnowledgeBaseAccess(kb, "KB_DELETE");
        List<Document> documents = documentRepository.findByKnowledgeBaseId(id);
        for (Document doc : documents) {
            vectorStorePort.deleteByDocumentId(id, doc.getId());
            chunkSearchIndexPort.deleteByDocumentId(id, doc.getId());
            documentRepository.deleteById(doc.getId());
        }
        knowledgeBaseRepository.deleteById(id);
    }

    @Override
    public void delete(DocumentId id) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new DomainException("未找到文档: " + id.value()));
        KnowledgeBase kb = requireKnowledgeBase(document.getKnowledgeBaseId());
        accessControlPort.checkKnowledgeBaseAccess(kb, "KB_DELETE");
        vectorStorePort.deleteByDocumentId(document.getKnowledgeBaseId(), id);
        chunkSearchIndexPort.deleteByDocumentId(document.getKnowledgeBaseId(), id);
        documentRepository.deleteById(id);
    }

    @Override
    public List<Document> listByKnowledgeBase(KnowledgeBaseId knowledgeBaseId) {
        KnowledgeBase kb = requireKnowledgeBase(knowledgeBaseId);
        accessControlPort.checkKnowledgeBaseAccess(kb, "KB_READ");
        return documentRepository.findByKnowledgeBaseId(knowledgeBaseId);
    }

    @Override
    public List<Document> listByKnowledgeBase(KnowledgeBaseId knowledgeBaseId, int page, int size) {
        KnowledgeBase kb = requireKnowledgeBase(knowledgeBaseId);
        accessControlPort.checkKnowledgeBaseAccess(kb, "KB_READ");
        return documentRepository.findByKnowledgeBaseId(knowledgeBaseId, page, size);
    }

    @Override
    public DocumentId ingest(IngestDocumentCommand command) {
        KnowledgeBase kb = requireKnowledgeBase(command.knowledgeBaseId());
        accessControlPort.checkKnowledgeBaseAccess(kb, "KB_WRITE");

        List<Document> existingDocs = documentRepository.findByKnowledgeBaseIdAndContentHash(
                command.knowledgeBaseId(), command.contentHash()
        );
        for (Document existing : existingDocs) {
            if (existing.getStatus() == DocumentStatus.PENDING
                    || existing.getStatus() == DocumentStatus.PROCESSING
                    || existing.getStatus() == DocumentStatus.COMPLETED) {
                throw new DomainException("此知识库已存在相同内容的文档");
            }
            if (existing.getStatus() == DocumentStatus.FAILED) {
                vectorStorePort.deleteByDocumentId(existing.getKnowledgeBaseId(), existing.getId());
                chunkSearchIndexPort.deleteByDocumentId(existing.getKnowledgeBaseId(), existing.getId());
                documentRepository.deleteById(existing.getId());
            }
        }

        Document document = Document.create(
                command.knowledgeBaseId(),
                command.fileName(),
                command.contentType(),
                command.fileSize(),
                command.contentHash()
        );

        document = documentRepository.save(document);

        Document processingDocument = document;
        ingestionExecutor.execute(() -> runIngestionTask(processingDocument, command.content()));

        return document.getId();
    }

    private void runIngestionTask(Document document, InputStream content) {
        try {
            processDocument(document, content);
            log.info("文档摄入完成 documentId={} knowledgeBaseId={} chunks={}",
                    document.getId().value(), document.getKnowledgeBaseId().value(), document.getChunkCount());
        } catch (DomainException e) {
            log.warn("文档摄入失败 documentId={} knowledgeBaseId={} reason={}",
                    document.getId().value(), document.getKnowledgeBaseId().value(), e.getMessage());
        } catch (Exception e) {
            log.error("文档摄入出现未预期异常 documentId={} knowledgeBaseId={}",
                    document.getId().value(), document.getKnowledgeBaseId().value(), e);
        }
    }

    private void processDocument(Document document, InputStream content) {
        try {
            document.transitionTo(DocumentStatus.PROCESSING);
            documentRepository.save(document);
            log.info("文档摄入开始 documentId={} knowledgeBaseId={}",
                    document.getId().value(), document.getKnowledgeBaseId().value());

            String text = documentParserPort.parse(content, document.getFileName());
            log.info("文档解析完成 documentId={} chars={}", document.getId().value(), text.length());

            List<DocumentChunk> chunks = recursiveChunkingStrategy.split(text);
            if (chunks.isEmpty()) {
                throw new DomainException("文档未解析出有效文本");
            }
            log.info("文档分块完成 documentId={} chunks={}", document.getId().value(), chunks.size());

            List<float[]> embeddings = embeddingPort.embed(
                    chunks.stream().map(DocumentChunk::text).toList()
            );
            log.info("文档向量化完成 documentId={} embeddings={}", document.getId().value(), embeddings.size());

            vectorStorePort.store(
                    document.getKnowledgeBaseId(),
                    document.getId(),
                    document.getFileName(),
                    chunks,
                    embeddings
            );
            log.info("文档向量写入完成 documentId={}", document.getId().value());

            chunkSearchIndexPort.store(
                    document.getKnowledgeBaseId(),
                    document.getId(),
                    document.getFileName(),
                    chunks
            );
            log.info("文档关键词索引写入完成 documentId={}", document.getId().value());

            document.setChunkCount(chunks.size());
            document.transitionTo(DocumentStatus.COMPLETED);

        } catch (Exception e) {
            try {
                vectorStorePort.deleteByDocumentId(document.getKnowledgeBaseId(), document.getId());
            } catch (Exception ignored) {
                // 保留主异常；失败文档重新上传时会再次清理残留向量。
            }
            try {
                chunkSearchIndexPort.deleteByDocumentId(document.getKnowledgeBaseId(), document.getId());
            } catch (Exception ignored) {
                // 保留主异常；失败文档重新上传时会再次清理残留索引。
            }
            document.transitionTo(DocumentStatus.FAILED, safeFailureReason(e));
            throw new DomainException("文档处理失败: " + safeFailureReason(e), e);
        } finally {
            documentRepository.save(document);
        }
    }

    @Override
    public RagContext prepare(Query query) {
        KnowledgeBase kb = requireKnowledgeBase(query.knowledgeBaseId());
        accessControlPort.checkKnowledgeBaseAccess(kb, "KB_READ");
        ChatSession chatSession = resolveChatSession(query, kb);
        if (chatMemoryEnabled) {
            appendMessage(chatSession, ChatRole.USER, query.text(), List.of());
            chatSession.renameFromUserQuestion(query.text());
            chatSessionRepository.save(chatSession);
            summarizeIfNeeded(chatSession);
        }
        PreparedQuery preparedQuery = prepareQuery(query.text());

        CompletableFuture<List<ChunkReference>> vectorFuture = CompletableFuture.supplyAsync(
                () -> vectorStorePort.search(query.knowledgeBaseId(), preparedQuery.embedding(), vectorCandidateK),
                retrievalExecutor
        );
        CompletableFuture<List<ChunkReference>> keywordFuture = CompletableFuture.supplyAsync(
                () -> chunkSearchIndexPort.search(query.knowledgeBaseId(), preparedQuery.effectiveText(), keywordCandidateK),
                retrievalExecutor
        );

        List<ChunkReference> results = mergeAndRerank(await(vectorFuture), await(keywordFuture));

        StringBuilder contextBuilder = new StringBuilder();
        appendMemoryContext(contextBuilder, chatSession);
        List<ChunkReference> references = new ArrayList<>();

        for (int i = 0; i < results.size(); i++) {
            ChunkReference result = results.get(i);

            contextBuilder.append("<reference index=\"").append(i + 1).append("\">\n")
                    .append(result.chunkText()).append("\n</reference>\n\n");

            references.add(result);
        }

        String prompt = String.format(promptTemplate,
                contextBuilder.toString(),
                query.text()
        );

        return new RagContext(
                prompt,
                references,
                chatSession == null ? null : chatSession.getId().value(),
                chatSession == null ? null : chatSession.getOwnerUserId()
        );
    }

    @Override
    public void complete(RagContext ragContext, String answerText) {
        if (!chatMemoryEnabled || ragContext.sessionId() == null || answerText == null || answerText.isBlank()) {
            return;
        }
        ChatSession session = requireSessionForOwner(
                new ChatSessionId(ragContext.sessionId()),
                ragContext.ownerUserId()
        );
        appendMessage(session, ChatRole.ASSISTANT, answerText, ragContext.references());
        chatSessionRepository.save(session);
    }

    @Override
    public ChatSession getOrCreateCurrentSession(KnowledgeBaseId knowledgeBaseId) {
        KnowledgeBase kb = requireKnowledgeBase(knowledgeBaseId);
        accessControlPort.checkKnowledgeBaseAccess(kb, "KB_READ");
        String ownerUserId = accessControlPort.currentUserId();
        return chatSessionRepository.findLatestByOwnerUserIdAndKnowledgeBaseId(ownerUserId, knowledgeBaseId)
                .orElseGet(() -> chatSessionRepository.save(ChatSession.create(ownerUserId, knowledgeBaseId)));
    }

    @Override
    public ChatSession createChatSession(KnowledgeBaseId knowledgeBaseId) {
        KnowledgeBase kb = requireKnowledgeBase(knowledgeBaseId);
        accessControlPort.checkKnowledgeBaseAccess(kb, "KB_READ");
        return chatSessionRepository.save(ChatSession.create(accessControlPort.currentUserId(), knowledgeBaseId));
    }

    @Override
    public List<ChatMessage> listChatMessages(ChatSessionId sessionId, int page, int size) {
        requireSessionForCurrentUser(sessionId);
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 100));
        return chatMessageRepository.findBySessionId(sessionId, safePage, safeSize);
    }

    private ChatSession resolveChatSession(Query query, KnowledgeBase kb) {
        if (!chatMemoryEnabled) {
            return null;
        }
        if (query.sessionId() != null) {
            ChatSession session = requireSessionForCurrentUser(new ChatSessionId(query.sessionId()));
            if (!session.getKnowledgeBaseId().equals(query.knowledgeBaseId())) {
                throw new DomainException("聊天会话不属于当前知识库");
            }
            return session;
        }
        return chatSessionRepository
                .findLatestByOwnerUserIdAndKnowledgeBaseId(accessControlPort.currentUserId(), kb.getId())
                .orElseGet(() -> chatSessionRepository.save(ChatSession.create(accessControlPort.currentUserId(), kb.getId())));
    }

    private ChatSession requireSessionForCurrentUser(ChatSessionId sessionId) {
        return requireSessionForOwner(sessionId, accessControlPort.currentUserId());
    }

    private ChatSession requireSessionForOwner(ChatSessionId sessionId, String ownerUserId) {
        ChatSession session = chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> new DomainException("未找到聊天会话: " + sessionId.value()));
        if (ownerUserId == null || !ownerUserId.equals(session.getOwnerUserId())) {
            throw new DomainException("无权访问该聊天会话");
        }
        return session;
    }

    private void appendMessage(ChatSession session, ChatRole role, String content, List<ChunkReference> references) {
        ChatMessage message = ChatMessage.create(session, role, content, references);
        chatMessageRepository.save(message);
    }

    private void summarizeIfNeeded(ChatSession session) {
        long memoryMaxSequence = Math.max(0, session.getMessageCount() - 1);
        long unsummarizedHistoryCount = memoryMaxSequence - session.getSummarizedUntilSequence();
        if (unsummarizedHistoryCount <= summaryTriggerMessageCount) {
            return;
        }

        long summarizeUntil = Math.max(session.getSummarizedUntilSequence(), memoryMaxSequence - recentMessageLimit);
        if (summarizeUntil <= session.getSummarizedUntilSequence()) {
            return;
        }
        List<ChatMessage> messages = chatMessageRepository.findBySessionIdAndSequenceBetween(
                session.getId(),
                session.getSummarizedUntilSequence(),
                summarizeUntil
        );
        if (messages.isEmpty()) {
            return;
        }

        try {
            String summary = chatMemorySummarizerPort.summarize(session.getSummary(), messages, maxSummaryChars);
            session.updateSummary(summary, summarizeUntil);
            chatSessionRepository.save(session);
            log.debug("聊天记忆摘要完成 sessionId={} summarizedUntil={}",
                    session.getId().value(), summarizeUntil);
        } catch (Exception e) {
            log.warn("聊天记忆摘要失败，回退最近消息 sessionId={} reason={}",
                    session.getId().value(), e.getMessage());
        }
    }

    private void appendMemoryContext(StringBuilder contextBuilder, ChatSession session) {
        if (!chatMemoryEnabled || session == null) {
            return;
        }
        if (session.getSummary() != null && !session.getSummary().isBlank()) {
            contextBuilder.append("<conversation_summary>\n")
                    .append(session.getSummary())
                    .append("\n</conversation_summary>\n\n");
        }

        long memoryMaxSequence = Math.max(0, session.getMessageCount() - 1);
        List<ChatMessage> recentMessages = recentMessageLimit == 0
                ? List.of()
                : chatMessageRepository.findRecentBySessionIdBeforeOrEqual(
                        session.getId(), memoryMaxSequence, recentMessageLimit);
        if (recentMessages.isEmpty()) {
            return;
        }

        contextBuilder.append("<recent_dialogue>\n");
        for (ChatMessage message : recentMessages) {
            contextBuilder.append(message.role() == ChatRole.USER ? "用户: " : "助手: ")
                    .append(message.content())
                    .append('\n');
        }
        contextBuilder.append("</recent_dialogue>\n\n");
    }

    private PreparedQuery prepareQuery(String originalText) {
        float[] originalEmbedding = embeddingPort.embed(originalText);
        if (!queryRewriteEnabled) {
            return new PreparedQuery(originalText, originalEmbedding);
        }

        try {
            String rewritten = queryRewritePort.rewrite(originalText);
            if (rewritten == null || rewritten.isBlank() || rewritten.equals(originalText)) {
                return new PreparedQuery(originalText, originalEmbedding);
            }

            float[] rewrittenEmbedding = embeddingPort.embed(rewritten);
            double similarity = cosineSimilarity(originalEmbedding, rewrittenEmbedding);
            if (similarity < queryRewriteSimilarityThreshold) {
                log.info("Query 改写未通过相似度门禁 similarity={} threshold={}",
                        String.format("%.4f", similarity), queryRewriteSimilarityThreshold);
                return new PreparedQuery(originalText, originalEmbedding);
            }

            log.debug("Query 改写生效 similarity={}", String.format("%.4f", similarity));
            return new PreparedQuery(rewritten, rewrittenEmbedding);
        } catch (Exception e) {
            log.warn("Query 改写失败，回退原始查询: {}", e.getMessage());
            return new PreparedQuery(originalText, originalEmbedding);
        }
    }

    private List<ChunkReference> mergeAndRerank(List<ChunkReference> vectorResults, List<ChunkReference> keywordResults) {
        Map<String, RankedReference> merged = new LinkedHashMap<>();
        for (ChunkReference reference : vectorResults) {
            merged.computeIfAbsent(referenceKey(reference), key -> new RankedReference(reference))
                    .vectorScore = reference.score();
        }
        for (ChunkReference reference : keywordResults) {
            merged.computeIfAbsent(referenceKey(reference), key -> new RankedReference(reference))
                    .keywordScore = reference.score();
        }

        return merged.values().stream()
                .map(this::toFinalReference)
                .sorted(Comparator.comparing(ChunkReference::score).reversed())
                .limit(topK)
                .toList();
    }

    private ChunkReference toFinalReference(RankedReference ranked) {
        ChunkReference ref = ranked.reference;
        float score = (float) Math.min(1.0, vectorWeight * ranked.vectorScore + keywordWeight * ranked.keywordScore);
        return new ChunkReference(
                ref.documentId(),
                ref.documentName(),
                ref.chunkIndex(),
                ref.chunkText(),
                score,
                ref.startPosition(),
                ref.endPosition()
        );
    }

    private String referenceKey(ChunkReference reference) {
        return reference.documentId() + ":" + reference.chunkIndex();
    }

    private List<ChunkReference> await(CompletableFuture<List<ChunkReference>> future) {
        try {
            return future.join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new DomainException("检索失败", e);
        }
    }

    private double cosineSimilarity(float[] left, float[] right) {
        if (left.length != right.length) {
            throw new DomainException("向量维度不一致");
        }
        double dot = 0;
        double leftNorm = 0;
        double rightNorm = 0;
        for (int i = 0; i < left.length; i++) {
            dot += left[i] * right[i];
            leftNorm += left[i] * left[i];
            rightNorm += right[i] * right[i];
        }
        if (leftNorm == 0 || rightNorm == 0) {
            return 0;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    private KnowledgeBase requireKnowledgeBase(KnowledgeBaseId id) {
        return knowledgeBaseRepository.findById(id)
                .orElseThrow(() -> new DomainException("未找到知识库: " + id.value()));
    }

    private List<KnowledgeBase> filterVisible(List<KnowledgeBase> knowledgeBases) {
        if (accessControlPort.isAdmin()) {
            return knowledgeBases;
        }
        String currentUserId = accessControlPort.currentUserId();
        return knowledgeBases.stream()
                .filter(kb -> currentUserId.equals(kb.getOwnerUserId()))
                .toList();
    }

    private String safeFailureReason(Exception e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) {
            message = e.getClass().getSimpleName();
        }
        return message.length() <= MAX_FAILURE_REASON_LENGTH
                ? message
                : message.substring(0, MAX_FAILURE_REASON_LENGTH);
    }

    private record PreparedQuery(String effectiveText, float[] embedding) {
    }

    private static class RankedReference {
        private final ChunkReference reference;
        private float vectorScore;
        private float keywordScore;

        private RankedReference(ChunkReference reference) {
            this.reference = reference;
        }
    }
}
