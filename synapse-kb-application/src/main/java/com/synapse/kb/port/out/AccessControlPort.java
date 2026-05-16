package com.synapse.kb.port.out;

import com.synapse.kb.model.KnowledgeBase;

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
