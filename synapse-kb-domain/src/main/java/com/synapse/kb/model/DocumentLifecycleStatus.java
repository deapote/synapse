package com.synapse.kb.model;

/** 文档业务时效状态。与 {@link DocumentStatus}（摄入状态机）严格分离。 */
public enum DocumentLifecycleStatus {
    ACTIVE,
    SUPERSEDED,
    RETIRED
}
