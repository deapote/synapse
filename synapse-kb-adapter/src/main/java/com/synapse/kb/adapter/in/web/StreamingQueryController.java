package com.synapse.kb.adapter.in.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.synapse.kb.adapter.in.web.dto.ChunkReferenceResponse;
import com.synapse.kb.adapter.in.web.dto.QueryRequest;
import com.synapse.kb.model.ChunkReference;
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
 * 流式知识库问答 Web 控制器（入站适配器）。
 *
 * <p>处理 SSE 流式问答 HTTP 请求，调用 {@link QueryKnowledgeBaseUseCase#prepare}
 * 完成检索 + prompt 组装，再通过 {@link StreamingLlmPort} 调用 LLM 流式生成回答。
 *
 * <p>SSE 事件协议：
 * <ul>
 *   <li>{@code event: token} —— 文本片段，data 为 {@code {"token":"..."}}</li>
 *   <li>{@code event: references} —— 引用来源，data 为 {@code {"references":[...]}}</li>
 *   <li>{@code event: complete} —— 流结束，data 为 {@code {}}</li>
 *   <li>{@code event: error} —— 错误，data 为 {@code {"message":"..."}}</li>
 * </ul>
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

    /**
     * 流式问答接口（SSE）。
     *
     * <p>完整 RAG 流程：
     * <ol>
     *   <li>用户问题向量化 → 向量检索 topK 相似片段</li>
     *   <li>组装 prompt（上下文 + 问题）</li>
     *   <li>调用 LLM 流式生成回答，通过 SSE 逐 token 推送</li>
     *   <li>推送引用来源后结束</li>
     * </ol>
     *
     * @param kbId    知识库 ID
     * @param request 查询请求，包含用户问题
     * @return SSE 事件流
     */
    @PostMapping(value = "/api/knowledge-bases/{kbId}/query/stream",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> queryStream(
            @PathVariable String kbId,
            @RequestBody QueryRequest request
    ) {
        return Mono.fromCallable(() -> queryUseCase.prepare(new Query(kbId, request.query())))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(this::buildSseFlux)
                .onErrorResume(e -> {
                    log.error("流式问答失败", e);
                    return Flux.just(sseEvent("error", Map.of("message", e.getMessage())));
                });
    }

    /**
     * 构建 SSE 事件流。
     *
     * <p>事件顺序：token（多个）→ references → complete
     *
     * <p>使用 {@link Flux#using} 管理 {@link Stream} 生命周期，
     * 确保取消或异常时 {@code Stream#close()} 被调用，进而触发 LLM 线程中断。
     */
    private Flux<ServerSentEvent<String>> buildSseFlux(RagContext ragContext) {
        return Flux.using(
                () -> streamingLlmPort.generateStream(ragContext.prompt()),
                stream -> {
                    Flux<ServerSentEvent<String>> tokenFlux = Flux.fromStream(stream)
                            .doOnCancel(() -> log.debug("SSE 连接被取消（用户停止生成）"))
                            .map(token -> sseEvent("token", Map.of("token", token)));

                    List<ChunkReferenceResponse> references = ragContext.references().stream()
                            .map(this::toResponse)
                            .toList();

                    ServerSentEvent<String> refsEvent = sseEvent("references", Map.of("references", references));
                    ServerSentEvent<String> completeEvent = sseEvent("complete", Map.of());

                    return Flux.concat(tokenFlux, Flux.just(refsEvent, completeEvent));
                },
                Stream::close
        );
    }

    /**
     * 将领域对象转为响应 DTO。
     */
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

    /**
     * 构建 SSE 事件。
     *
     * @param eventName 事件类型（token / references / complete / error）
     * @param data      事件数据，会被序列化为 JSON
     * @return SSE 事件对象
     */
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
