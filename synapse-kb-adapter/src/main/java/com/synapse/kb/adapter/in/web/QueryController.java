package com.synapse.kb.adapter.in.web;

import com.synapse.kb.adapter.in.web.dto.AnswerResponse;
import com.synapse.kb.adapter.in.web.dto.ChunkReferenceResponse;
import com.synapse.kb.adapter.in.web.dto.QueryRequest;
import com.synapse.kb.model.ChunkReference;
import com.synapse.kb.model.Query;
import com.synapse.kb.model.RagContext;
import com.synapse.kb.port.in.QueryKnowledgeBaseUseCase;
import com.synapse.kb.port.out.LlmPort;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

/**
 * 知识库问答 Web 控制器（入站适配器）。
 *
 * <p>处理用户问答 HTTP 请求，调用 {@link QueryKnowledgeBaseUseCase} 完成检索 + prompt 组装，
 * 再通过 {@link LlmPort} 调用 LLM 生成回答。返回的 {@link AnswerResponse} 包含完整回答文本
 * 及引用来源列表，供前端展示。
 *
 * <p>基础路径：{@code /api/knowledge-bases/{kbId}/query}
 */
@RestController
public class QueryController {

    private final QueryKnowledgeBaseUseCase queryUseCase;
    private final LlmPort llmPort;

    public QueryController(QueryKnowledgeBaseUseCase queryUseCase, LlmPort llmPort) {
        this.queryUseCase = queryUseCase;
        this.llmPort = llmPort;
    }

    /**
     * 同步问答接口。
     *
     * <p>完整 RAG 流程：
     * <ol>
     *   <li>用户问题向量化 → 向量检索 topK 相似片段</li>
     *   <li>组装 prompt（上下文 + 问题）</li>
     *   <li>调用 LLM 生成回答</li>
     *   <li>返回回答 + 引用来源</li>
     * </ol>
     *
     * @param kbId    知识库 ID
     * @param request 查询请求，包含用户问题
     * @return 回答文本及引用来源列表
     */
    @PostMapping("/api/knowledge-bases/{kbId}/query")
    public Mono<AnswerResponse> query(
            @PathVariable String kbId,
            @RequestBody QueryRequest request
    ) {
        return Mono.fromCallable(() -> {
            RagContext ragContext = queryUseCase.prepare(
                    new Query(kbId, request.query())
            );

            String answer = llmPort.generate(ragContext.prompt());

            List<ChunkReferenceResponse> references =
                    ragContext.references().stream()
                            .map(ref -> new ChunkReferenceResponse(
                                    ref.documentId(),
                                    ref.documentName(),
                                    ref.chunkText(),
                                    ref.score(),
                                    ref.startPosition(),
                                    ref.endPosition()
                            ))
                            .toList();

            return new AnswerResponse(answer, references);
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
