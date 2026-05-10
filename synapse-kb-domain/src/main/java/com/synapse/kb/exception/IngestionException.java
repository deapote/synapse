package com.synapse.kb.exception;

import com.synapse.shared.exception.DomainException;

/**
 * 文档摄入异常。
 *
 * <p>文档摄入流程（解析 → 分块 → 向量化 → 存储）中任一环节失败时抛出，
 * 用于区分摄入阶段的技术/业务错误与普通的领域校验错误。
 *
 * <p>捕获后通常应：记录失败原因、更新 {@code Document} 状态为 {@code FAILED}、
 * 并允许通过 {@code FAILED → PENDING} 重试。
 */
public class IngestionException extends DomainException {

    /**
     * 构造摄入异常。
     *
     * @param message 错误描述
     */
    public IngestionException(String message) {
        super(message);
    }

    /**
     * 构造摄入异常，包装底层异常。
     *
     * @param message 错误描述
     * @param cause   底层异常，保留原始堆栈
     */
    public IngestionException(String message, Throwable cause) {
        super(message, cause);
    }
}
