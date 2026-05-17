package com.synapse.kb.adapter.in.web;

import com.synapse.kb.adapter.in.web.dto.ChatMessageResponse;
import com.synapse.kb.adapter.in.web.dto.ChatSessionResponse;
import com.synapse.kb.adapter.in.web.dto.ChunkReferenceResponse;
import com.synapse.kb.model.ChatMessage;
import com.synapse.kb.model.ChatRole;
import com.synapse.kb.model.ChatSession;
import com.synapse.kb.model.ChatSessionId;
import com.synapse.kb.model.ChunkReference;
import com.synapse.kb.model.KnowledgeBaseId;
import com.synapse.kb.port.in.CreateChatSessionUseCase;
import com.synapse.kb.port.in.GetCurrentChatSessionUseCase;
import com.synapse.kb.port.in.ListChatMessagesUseCase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
public class ChatController {

    private final GetCurrentChatSessionUseCase getCurrentChatSessionUseCase;
    private final CreateChatSessionUseCase createChatSessionUseCase;
    private final ListChatMessagesUseCase listChatMessagesUseCase;
    private final int maxPageSize;

    public ChatController(GetCurrentChatSessionUseCase getCurrentChatSessionUseCase,
                          CreateChatSessionUseCase createChatSessionUseCase,
                          ListChatMessagesUseCase listChatMessagesUseCase,
                          @Value("${synapse.web.max-page-size:100}") int maxPageSize) {
        this.getCurrentChatSessionUseCase = getCurrentChatSessionUseCase;
        this.createChatSessionUseCase = createChatSessionUseCase;
        this.listChatMessagesUseCase = listChatMessagesUseCase;
        this.maxPageSize = Math.max(1, maxPageSize);
    }

    @GetMapping("/api/knowledge-bases/{kbId}/chat/sessions/current")
    public Mono<ChatSessionResponse> currentSession(@PathVariable String kbId) {
        return SaTokenReactorBridge.blockingCall(
                () -> toResponse(getCurrentChatSessionUseCase.getOrCreateCurrentSession(new KnowledgeBaseId(kbId))));
    }

    @PostMapping("/api/knowledge-bases/{kbId}/chat/sessions")
    public Mono<ChatSessionResponse> createSession(@PathVariable String kbId) {
        return SaTokenReactorBridge.blockingCall(
                () -> toResponse(createChatSessionUseCase.createChatSession(new KnowledgeBaseId(kbId))));
    }

    @GetMapping("/api/chat/sessions/{sessionId}/messages")
    public Mono<List<ChatMessageResponse>> messages(@PathVariable String sessionId,
                                                    @RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "50") int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, maxPageSize));
        return SaTokenReactorBridge.blockingCall(
                () -> listChatMessagesUseCase.listChatMessages(new ChatSessionId(sessionId), safePage, safeSize)
                        .stream()
                        .map(this::toResponse)
                        .toList());
    }

    private ChatSessionResponse toResponse(ChatSession session) {
        return new ChatSessionResponse(
                session.getId().value(),
                session.getKnowledgeBaseId().value(),
                session.getTitle(),
                session.getSummary(),
                session.getMessageCount(),
                session.getCreatedAt(),
                session.getUpdatedAt()
        );
    }

    private ChatMessageResponse toResponse(ChatMessage message) {
        return new ChatMessageResponse(
                message.id(),
                message.sessionId().value(),
                message.role() == ChatRole.USER ? "user" : "assistant",
                message.content(),
                message.references().stream().map(this::toResponse).toList(),
                message.sequence(),
                message.createdAt()
        );
    }

    private ChunkReferenceResponse toResponse(ChunkReference ref) {
        return new ChunkReferenceResponse(
                ref.documentId(),
                ref.documentName(),
                ref.chunkText(),
                ref.score(),
                ref.startPosition(),
                ref.endPosition()
        );
    }
}
