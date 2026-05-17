package com.synapse.kb.model;

/** 文档处理状态；合法流转由 {@link Document} 统一校验。 */
public enum DocumentStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED,
}
