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
 * 知识库接口适配器，负责分页边界和 WebFlux 到同步用例的桥接。
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
