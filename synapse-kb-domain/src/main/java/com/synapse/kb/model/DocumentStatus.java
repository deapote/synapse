package com.synapse.kb.model;

/**
 * 文档处理状态。
 *
 * <p>状态流转规则：
 * <ul>
 *   <li>PENDING → PROCESSING → COMPLETED</li>
 *   <li>PENDING → PROCESSING → FAILED</li>
 *   <li>FAILED → PENDING（支持重试）</li>
 * </ul>
 */
public enum DocumentStatus {
    /** 等待处理 */
    PENDING,

    /** 处理中 */
    PROCESSING,

    /** 处理完成 */
    COMPLETED,

    /** 处理失败 */
    FAILED,
}
