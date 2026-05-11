package com.synapse.kb.adapter.in.web;

import com.synapse.kb.adapter.in.web.dto.CreateKnowledgeBaseRequest;
import com.synapse.kb.adapter.in.web.dto.KnowledgeBaseResponse;
import com.synapse.kb.model.KnowledgeBaseId;
import com.synapse.kb.port.in.CreateKnowledgeBaseUseCase;
import com.synapse.kb.port.in.DeleteKnowledgeBaseUseCase;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;

/**
 * 知识库管理 Web 控制器（入站适配器）。
 *
 * <p>处理知识库的增删查改 HTTP 请求，调用 application 层 UseCase 完成业务逻辑。
 * 所有端点返回 {@code Mono<>}，内部通过 {@link Schedulers#boundedElastic()} 将同步调用异步化，
 * 避免阻塞 Netty 事件循环线程。
 *
 * <p>基础路径：{@code /api/knowledge-bases}
 */
@RestController
@RequestMapping("/api/knowledge-bases")
public class KnowledgeBaseController {

    private final CreateKnowledgeBaseUseCase createUseCase;
    private final DeleteKnowledgeBaseUseCase deleteUseCase;

    public KnowledgeBaseController(CreateKnowledgeBaseUseCase createUseCase,
                                   DeleteKnowledgeBaseUseCase deleteUseCase) {
        this.createUseCase = createUseCase;
        this.deleteUseCase = deleteUseCase;
    }

    /**
     * 创建知识库。
     *
     * @param request 创建请求，包含名称和描述
     * @return 新创建的知识库信息（含生成的 ID）
     */
    @PostMapping
    public Mono<KnowledgeBaseResponse> create(@RequestBody CreateKnowledgeBaseRequest request) {
        return Mono.fromCallable(() -> {
            KnowledgeBaseId id = createUseCase.create(
                    new CreateKnowledgeBaseUseCase.CreateKnowledgeBaseCommand(
                            request.name(), request.description()
                    )
            );
            return new KnowledgeBaseResponse(
                    id.value(), request.name(), request.description(), Instant.now()
            );
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 删除知识库（级联删除其下所有文档及向量）。
     *
     * @param id 知识库唯一标识
     * @return 空响应，删除成功后返回 200 OK
     */
    @DeleteMapping("/{id}")
    public Mono<Void> delete(@PathVariable String id) {
        return Mono.<Void>fromCallable(() -> {
            deleteUseCase.delete(new KnowledgeBaseId(id));
            return null;
        }).subscribeOn(Schedulers.boundedElastic());
    }
}