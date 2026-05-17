package com.synapse.kb.adapter.in.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.synapse.kb.adapter.in.web.dto.ChunkReferenceResponse;
import com.synapse.kb.adapter.in.web.dto.QueryRequest;
import com.synapse.kb.model.ChunkReference;
import com.synapse.kb.model.KnowledgeBaseId;
import com.synapse.kb.model.Query;
import com.synapse.kb.model.RagContext;
import com.synapse.kb.port.in.QueryKnowledgeBaseUseCase;
import com.synapse.kb.port.out.StreamingLlmPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * SSE 问答接口适配器。事件协议：session、token、references、complete、error。
 */
@RestController
public class StreamingQueryController {

    private static final Logger log = LoggerFactory.getLogger(StreamingQueryController.class);

    private final QueryKnowledgeBaseUseCase queryUseCase;
    private final StreamingLlmPort streamingLlmPort;
    private final ObjectMapper objectMapper;

    public StreamingQueryController(QueryKnowledgeBaseUseCase queryUseCase,
                                    StreamingLlmPort streamingLlmPort,
                                    ObjectMapper objectMapper) {
        this.queryUseCase = queryUseCase;
        this.streamingLlmPort = streamingLlmPort;
        this.objectMapper = objectMapper;
    }

    @PostMapping(value = "/api/knowledge-bases/{kbId}/query/stream",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> queryStream(
            @PathVariable String kbId,
            @RequestBody QueryRequest request
    ) {
        return SaTokenReactorBridge.blockingCall(
                        () -> queryUseCase.prepare(new Query(new KnowledgeBaseId(kbId), request.query(), request.sessionId())))
                .flatMapMany(this::buildSseFlux)
                .onErrorResume(e -> {
                    log.error("流式问答失败", e);
                    return Flux.just(sseEvent("error", Map.of("message", e.getMessage())));
                });
    }

    /**
     * 使用 {@link Flux#using} 绑定 Stream 生命周期，确保客户端断开时取消 LLM 生成。
     */
    private Flux<ServerSentEvent<String>> buildSseFlux(RagContext ragContext) {
        return Flux.using(
                () -> streamingLlmPort.generateStream(ragContext.prompt()),
                stream -> {
                    StringBuilder answerBuilder = new StringBuilder();
                    Flux<ServerSentEvent<String>> tokenFlux = Flux.fromStream(stream)
                            .doOnNext(answerBuilder::append)
                            .doOnCancel(() -> log.debug("SSE 连接被取消（用户停止生成）"))
                            .map(token -> sseEvent("token", Map.of("token", token)));

                    List<ChunkReferenceResponse> references = ragContext.references().stream()
                            .map(this::toResponse)
                            .toList();

                    Flux<ServerSentEvent<String>> sessionFlux = ragContext.sessionId() == null
                            ? Flux.empty()
                            : Flux.just(sseEvent("session", Map.of("sessionId", ragContext.sessionId())));
                    ServerSentEvent<String> refsEvent = sseEvent("references", Map.of("references", references));
                    Mono<ServerSentEvent<String>> completeEvent = Mono.fromCallable(() -> {
                                queryUseCase.complete(ragContext, answerBuilder.toString());
                                Object payload = ragContext.sessionId() == null
                                        ? Map.of()
                                        : Map.of("sessionId", ragContext.sessionId());
                                return sseEvent("complete", payload);
                            })
                            .subscribeOn(Schedulers.boundedElastic())
                            .onErrorResume(e -> {
                                log.error("保存聊天回复失败 sessionId={}", ragContext.sessionId(), e);
                                return Mono.just(sseEvent("error", Map.of("message", "回答已生成但保存聊天记录失败")));
                            });

                    return Flux.concat(sessionFlux, tokenFlux, Flux.just(refsEvent), completeEvent);
                },
                Stream::close
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

    private ServerSentEvent<String> sseEvent(String eventName, Object data) {
        try {
            return ServerSentEvent.<String>builder()
                    .event(eventName)
                    .data(objectMapper.writeValueAsString(data))
                    .build();
        } catch (JsonProcessingException e) {
            throw new RuntimeException("SSE 序列化失败", e);
        }
    }
}
