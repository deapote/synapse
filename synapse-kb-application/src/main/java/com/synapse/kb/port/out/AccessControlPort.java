package com.synapse.kb.port.out;

import com.synapse.kb.model.KnowledgeBase;

/**
 * 权限检查端口，由 auth-adapter 实现。
 * 提供当前用户身份、权限校验及知识库访问控制。
 */
public interface AccessControlPort {
    String currentUserId();

    void checkPermission(String permission);

    boolean isAdmin();

    default void checkKnowledgeBaseAccess(KnowledgeBase knowledgeBase, String permission) {
        checkPermission(permission);
        if (!isAdmin() && !currentUserId().equals(knowledgeBase.getOwnerUserId())) {
            throw new com.synapse.shared.exception.DomainException("无权访问该知识库");
        }
    }
}
