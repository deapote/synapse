package com.synapse.kb.adapter.in.web;

import com.synapse.kb.adapter.in.web.dto.CreateKnowledgeBaseRequest;
import com.synapse.kb.adapter.in.web.dto.KnowledgeBaseResponse;
import com.synapse.kb.model.KnowledgeBase;
import com.synapse.kb.model.KnowledgeBaseId;
import com.synapse.kb.port.in.CreateKnowledgeBaseUseCase;
import com.synapse.kb.port.in.DeleteKnowledgeBaseUseCase;
import com.synapse.kb.port.in.ListKnowledgeBaseUseCase;
import com.synapse.kb.port.out.AccessControlPort;
import com.synapse.shared.exception.DomainException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;

/**
 * 知识库管理 Web 控制器（入站适配器）。
 *
 * <p>处理知识库的增删查改 HTTP 请求，调用 application 层 UseCase 完成业务逻辑。
 * 所有端点返回 {@code Mono<>}，内部将同步调用异步化，
 * 避免阻塞 Netty 事件循环线程。
 *
 * <p>基础路径：{@code /api/knowledge-bases}
 */
@RestController
@RequestMapping("/api/knowledge-bases")
public class KnowledgeBaseController {

    private final CreateKnowledgeBaseUseCase createUseCase;
    private final DeleteKnowledgeBaseUseCase deleteUseCase;
    private final ListKnowledgeBaseUseCase listUseCase;
    private final AccessControlPort accessControlPort;
    private final int maxPageSize;

    public KnowledgeBaseController(CreateKnowledgeBaseUseCase createUseCase,
                                   DeleteKnowledgeBaseUseCase deleteUseCase,
                                   ListKnowledgeBaseUseCase listUseCase,
                                   AccessControlPort accessControlPort,
                                   @Value("${synapse.web.max-page-size:100}") int maxPageSize) {
        this.createUseCase = createUseCase;
        this.deleteUseCase = deleteUseCase;
        this.listUseCase = listUseCase;
        this.accessControlPort = accessControlPort;
        this.maxPageSize = maxPageSize;
    }

    /**
     * 创建知识库。
     *
     * @param request 创建请求，包含名称和描述
     * @return 新创建的知识库信息（含生成的 ID）
     */
    @PostMapping
    public Mono<KnowledgeBaseResponse> create(@RequestBody CreateKnowledgeBaseRequest request) {
        return SaTokenReactorBridge.blockingCall(() -> {
            KnowledgeBaseId id = createUseCase.create(
                    new CreateKnowledgeBaseUseCase.CreateKnowledgeBaseCommand(
                            request.name(), request.description(), accessControlPort.currentUserId()
                    )
            );
            return new KnowledgeBaseResponse(
                    id.value(), request.name(), request.description(), accessControlPort.currentUserId(), Instant.now()
            );
        });
    }

    /**
     * 列出所有知识库（支持分页）。
     *
     * @param page 页码，默认 0
     * @param size 每页大小，默认 20
     * @return 知识库列表，按创建时间倒序排列
     */
    @GetMapping
    public Mono<List<KnowledgeBaseResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return SaTokenReactorBridge.blockingCall(() ->
                {
                    validatePage(page, size);
                    return listUseCase.listAll(page, size).stream()
                            .map(kb -> new KnowledgeBaseResponse(
                                    kb.getId().value(),
                                    kb.getName(),
                                    kb.getDescription(),
                                    kb.getOwnerUserId(),
                                    kb.getCreatedAt()
                            ))
                            .toList();
                }
        );
    }

    /**
     * 删除知识库（级联删除其下所有文档及向量）。
     *
     * @param id 知识库唯一标识
     * @return 空响应，删除成功后返回 200 OK
     */
    @DeleteMapping("/{id}")
    public Mono<Void> delete(@PathVariable String id) {
        return SaTokenReactorBridge.blockingAction(() -> deleteUseCase.delete(new KnowledgeBaseId(id)));
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > maxPageSize) {
            throw new DomainException("分页参数非法");
        }
    }
}
