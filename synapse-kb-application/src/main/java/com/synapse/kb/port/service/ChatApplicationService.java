package com.synapse.kb.port.service;

import com.synapse.kb.model.*;
import com.synapse.kb.port.in.CreateChatSessionUseCase;
import com.synapse.kb.port.in.GetCurrentChatSessionUseCase;
import com.synapse.kb.port.in.ListChatMessagesUseCase;
import com.synapse.kb.port.out.AccessControlPort;
import com.synapse.kb.port.out.ChatMemorySummarizerPort;
import com.synapse.kb.port.service.support.KnowledgeBaseAccessGuard;
import com.synapse.kb.repository.ChatMessageRepository;
import com.synapse.kb.repository.ChatSessionRepository;
import com.synapse.shared.exception.DomainException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 聊天应用服务，编排会话生命周期与记忆管理。
 * 负责会话创建、消息追加、最近消息加载及摘要压缩。
 */
public class ChatApplicationService implements
        GetCurrentChatSessionUseCase,
        CreateChatSessionUseCase,
        ListChatMessagesUseCase {

    private static final Logger log = LoggerFactory.getLogger(ChatApplicationService.class);
    private final KnowledgeBaseAccessGuard accessGuard;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final AccessControlPort accessControlPort;
    private final ChatMemorySummarizerPort chatMemorySummarizerPort;
    private final int recentMessageLimit;
    private final int summaryTriggerMessageCount;
    private final int maxSummaryChars;

    public ChatApplicationService(KnowledgeBaseAccessGuard accessGuard,
                                  ChatSessionRepository chatSessionRepository,
                                  ChatMessageRepository chatMessageRepository,
                                  AccessControlPort accessControlPort,
                                  ChatMemorySummarizerPort chatMemorySummarizerPort,
                                  int recentMessageLimit,
                                  int summaryTriggerMessageCount,
                                  int maxSummaryChars) {
        this.accessGuard = accessGuard;
        this.chatSessionRepository = chatSessionRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.accessControlPort = accessControlPort;
        this.chatMemorySummarizerPort = chatMemorySummarizerPort;
        this.recentMessageLimit = Math.max(0, Math.min(recentMessageLimit, 50));
        this.summaryTriggerMessageCount = Math.max(1, summaryTriggerMessageCount);
        this.maxSummaryChars = Math.max(200, maxSummaryChars);
    }

    @Override
    public ChatSession getOrCreateCurrentSession(KnowledgeBaseId knowledgeBaseId) {
        KnowledgeBase kb = accessGuard.requireKnowledgeBase(knowledgeBaseId);
        accessGuard.checkKnowledgeBaseAccess(kb, "KB_READ");
        String ownerUserId = accessControlPort.currentUserId();
        return chatSessionRepository.findLatestByOwnerUserIdAndKnowledgeBaseId(ownerUserId, knowledgeBaseId)
                .orElseGet(() -> chatSessionRepository.save(ChatSession.create(ownerUserId, knowledgeBaseId)));
    }

    @Override
    public ChatSession createChatSession(KnowledgeBaseId knowledgeBaseId) {
        KnowledgeBase kb = accessGuard.requireKnowledgeBase(knowledgeBaseId);
        accessGuard.checkKnowledgeBaseAccess(kb, "KB_READ");
        return chatSessionRepository.save(ChatSession.create(accessControlPort.currentUserId(), knowledgeBaseId));
    }

    @Override
    public List<ChatMessage> listChatMessages(ChatSessionId sessionId, int page, int size) {
        requireSessionForCurrentUser(sessionId);
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 100));
        return chatMessageRepository.findBySessionId(sessionId, safePage, safeSize);
    }

    public ChatSession resolveChatSessionForQuery(KnowledgeBaseId knowledgeBaseId, String sessionId) {
        if (sessionId != null) {
            ChatSession session = requireSessionForCurrentUser(new ChatSessionId(sessionId));
            if (!session.getKnowledgeBaseId().equals(knowledgeBaseId)) {
                throw new DomainException("聊天会话不属于当前知识库");
            }
            return session;
        }
        String ownerUserId = accessControlPort.currentUserId();
        return chatSessionRepository
                .findLatestByOwnerUserIdAndKnowledgeBaseId(ownerUserId, knowledgeBaseId)
                .orElseGet(() -> chatSessionRepository.save(ChatSession.create(ownerUserId, knowledgeBaseId)));
    }

    public void appendUserMessage(ChatSession session, String content) {
        appendMessage(session, ChatRole.USER, content, List.of());
    }

    public void appendAssistantMessage(ChatSession session, String content, List<ChunkReference> references) {
        appendMessage(session, ChatRole.ASSISTANT, content, references);
    }

    public void appendMemoryContext(StringBuilder contextBuilder, ChatSession session) {
        if (session == null) {
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

    public void summarizeIfNeeded(ChatSession session) {
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

    private void appendMessage(ChatSession session, ChatRole role, String content, List<ChunkReference> references) {
        long sequence = chatSessionRepository.nextMessageSequence(session.getId());
        session.recordMessageSequence(sequence);
        ChatMessage message = ChatMessage.createWithSequence(session, role, content, references, sequence);
        chatMessageRepository.save(message);
    }

    public ChatSession requireSessionForOwner(ChatSessionId sessionId, String ownerUserId) {
        ChatSession session = chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> new DomainException("未找到聊天会话: " + sessionId.value()));
        if (ownerUserId == null || !ownerUserId.equals(session.getOwnerUserId())) {
            throw new DomainException("无权访问该聊天会话");
        }
        return session;
    }

    private ChatSession requireSessionForCurrentUser(ChatSessionId sessionId) {
        return requireSessionForOwner(sessionId, accessControlPort.currentUserId());
    }

}
