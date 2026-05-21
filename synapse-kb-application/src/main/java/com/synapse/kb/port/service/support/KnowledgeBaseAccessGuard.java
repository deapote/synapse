package com.synapse.kb.port.service.support;

import com.synapse.kb.model.KnowledgeBase;
import com.synapse.kb.model.KnowledgeBaseId;
import com.synapse.kb.port.out.AccessControlPort;
import com.synapse.kb.repository.KnowledgeBaseRepository;
import com.synapse.shared.exception.DomainException;

import java.util.List;

/**
 * 知识库访问守卫。
 * 封装知识库存在性校验、权限检查及可见性过滤。
 */
public class KnowledgeBaseAccessGuard {
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final AccessControlPort accessControlPort;

    public KnowledgeBaseAccessGuard(KnowledgeBaseRepository knowledgeBaseRepository,
                                    AccessControlPort accessControlPort) {
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.accessControlPort = accessControlPort;
    }

    public KnowledgeBase requireKnowledgeBase(KnowledgeBaseId id) {
        return knowledgeBaseRepository.findById(id)
                .orElseThrow(() -> new DomainException("未找到知识库: " + id.value()));
    }

    public void checkKnowledgeBaseAccess(KnowledgeBase knowledgeBase, String permission) {
        accessControlPort.checkKnowledgeBaseAccess(knowledgeBase, permission);
    }

    public List<KnowledgeBase> filterVisible(List<KnowledgeBase> knowledgeBases) {
        if (accessControlPort.isAdmin()) {
            return knowledgeBases;
        }
        String currentUserId = accessControlPort.currentUserId();
        return knowledgeBases.stream()
                .filter(kb -> currentUserId.equals(kb.getOwnerUserId()))
                .toList();
    }
}
