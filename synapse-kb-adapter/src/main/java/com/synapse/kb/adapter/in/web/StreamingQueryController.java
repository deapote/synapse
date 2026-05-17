package com.synapse.kb.adapter.in.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.synapse.kb.adapter.in.web.dto.CitationValidationResponse;
import com.synapse.kb.adapter.in.web.dto.ChunkReferenceResponse;
import com.synapse.kb.adapter.in.web.dto.QueryRequest;
import com.synapse.kb.model.ChunkReference;
import com.synapse.kb.model.KnowledgeBaseId;
import com.synapse.kb.model.Query;
import com.synapse.kb.model.RagContext;
import com.synapse.kb.port.in.QueryKnowledgeBaseUseCase;
import com.synapse.kb.port.out.StreamingLlmPort;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * SSE 问答接口适配器。事件协议：session、token、references、validation、complete、error。
 */
@RestController
public class StreamingQueryController {

    private static final Logger log = LoggerFactory.getLogger(StreamingQueryController.class);
    private static final Pattern CITATION_PATTERN = Pattern.compile("\\[(\\d+)]");
    private static final Pattern ANSWER_SENTENCE_PATTERN = Pattern.compile("[^。！？!?\\n]+(?:[。！？!?]+\\s*(?:\\[\\d+])*)?");
    private static final Pattern INSUFFICIENT_PATTERN = Pattern.compile("知识库片段不足|资料不足|无法回答|不能回答|没有足够|未提供足够|不足以回答");

    private final QueryKnowledgeBaseUseCase queryUseCase;
    private final StreamingLlmPort streamingLlmPort;
    private final ObjectMapper objectMapper;
    private final Counter sseCancelledCounter;
    private final Counter sseErrorCounter;
    private final Timer firstTokenTimer;

    public StreamingQueryController(QueryKnowledgeBaseUseCase queryUseCase,
                                    StreamingLlmPort streamingLlmPort,
                                    ObjectMapper objectMapper,
                                    MeterRegistry meterRegistry) {
        this.queryUseCase = queryUseCase;
        this.streamingLlmPort = streamingLlmPort;
        this.objectMapper = objectMapper;
        this.sseCancelledCounter = Counter.builder("synapse.sse.cancelled").register(meterRegistry);
        this.sseErrorCounter = Counter.builder("synapse.sse.error").register(meterRegistry);
        this.firstTokenTimer = Timer.builder("synapse.llm.first_token").register(meterRegistry);
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
                    sseErrorCounter.increment();
                    log.error("流式问答失败", e);
                    return Flux.just(sseEvent("error", safeErrorPayload("流式问答失败")));
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
                    long startNanos = System.nanoTime();
                    boolean[] firstTokenSeen = {false};
                    Flux<ServerSentEvent<String>> tokenFlux = Flux.fromStream(stream)
                            .doOnNext(token -> {
                                if (!firstTokenSeen[0]) {
                                    firstTokenSeen[0] = true;
                                    firstTokenTimer.record(System.nanoTime() - startNanos, java.util.concurrent.TimeUnit.NANOSECONDS);
                                }
                                answerBuilder.append(token);
                            })
                            .doOnCancel(() -> {
                                sseCancelledCounter.increment();
                                log.debug("SSE 连接被取消（用户停止生成）");
                            })
                            .map(token -> sseEvent("token", Map.of("token", token)));

                    Flux<ServerSentEvent<String>> sessionFlux = ragContext.sessionId() == null
                            ? Flux.empty()
                            : Flux.just(sseEvent("session", Map.of("sessionId", ragContext.sessionId())));

                    Flux<ServerSentEvent<String>> terminalFlux = Flux.defer(() -> {
                        CitationValidationResponse validation = validateAnswer(
                                answerBuilder.toString(),
                                ragContext.references().size()
                        );
                        Set<Integer> usedSourceIds = new LinkedHashSet<>(validation.usedSourceIds());
                        List<ChunkReferenceResponse> references = IntStream.range(0, ragContext.references().size())
                                .mapToObj(index -> toResponse(
                                        ragContext.references().get(index),
                                        index + 1,
                                        usedSourceIds.contains(index + 1)))
                                .toList();
                        ServerSentEvent<String> refsEvent = sseEvent("references", Map.of("references", references));
                        ServerSentEvent<String> validationEvent = sseEvent("validation", validation);
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
                                return Mono.just(sseEvent("error", safeErrorPayload("回答已生成但保存聊天记录失败")));
                            });
                        return Flux.concat(Flux.just(refsEvent, validationEvent), completeEvent);
                    });

                    return Flux.concat(sessionFlux, tokenFlux, terminalFlux);
                },
                Stream::close
        );
    }

    private ChunkReferenceResponse toResponse(ChunkReference ref, int sourceId, boolean used) {
        return new ChunkReferenceResponse(
                sourceId,
                ref.documentId(),
                ref.documentName(),
                ref.chunkText(),
                ref.score(),
                ref.startPosition(),
                ref.endPosition(),
                used
        );
    }

    CitationValidationResponse validateAnswer(String answer, int referenceCount) {
        String safeAnswer = answer == null ? "" : answer.strip();
        Set<Integer> usedSourceIds = new LinkedHashSet<>();
        List<String> warnings = new ArrayList<>();

        Matcher matcher = CITATION_PATTERN.matcher(safeAnswer);
        while (matcher.find()) {
            int sourceId = Integer.parseInt(matcher.group(1));
            if (sourceId < 1 || sourceId > referenceCount) {
                warnings.add("回答引用了不存在的来源 [" + sourceId + "]");
                continue;
            }
            usedSourceIds.add(sourceId);
        }

        boolean insufficientAnswer = isInsufficientAnswer(safeAnswer);
        if (!safeAnswer.isBlank() && !insufficientAnswer) {
            if (referenceCount == 0) {
                warnings.add("回答没有可用检索来源");
            } else if (usedSourceIds.isEmpty()) {
                warnings.add("回答没有引用任何检索来源");
            } else {
                List<String> unsupportedSegments = unsupportedSegments(safeAnswer);
                if (!unsupportedSegments.isEmpty()) {
                    warnings.add("存在未带来源引用的事实性表述");
                }
            }
        }

        return new CitationValidationResponse(
                warnings.isEmpty(),
                List.copyOf(usedSourceIds),
                List.copyOf(warnings)
        );
    }

    private boolean isInsufficientAnswer(String answer) {
        return INSUFFICIENT_PATTERN.matcher(answer).find();
    }

    private List<String> unsupportedSegments(String answer) {
        List<String> segments = new ArrayList<>();
        Matcher sentenceMatcher = ANSWER_SENTENCE_PATTERN.matcher(answer);
        while (sentenceMatcher.find()) {
            String segment = sentenceMatcher.group().strip();
            if (segment.isBlank()
                    || isNonFactualLeadIn(segment)
                    || CITATION_PATTERN.matcher(segment).find()) {
                continue;
            }
            segments.add(segment);
        }
        return segments;
    }

    private boolean isNonFactualLeadIn(String segment) {
        String normalized = segment.replaceAll("\\s+", "");
        return normalized.length() <= 12
                || normalized.endsWith(":")
                || normalized.endsWith("：")
                || normalized.matches("(?i)^(以下|另外|综上|因此|总之|具体来说|建议|推荐).{0,18}[：:]?$");
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

    private Map<String, Object> safeErrorPayload(String message) {
        return Map.of("message", message, "traceId", TraceIdWebFilter.currentTraceId());
    }
}
