package com.synapse.kb.model;

/**
 * 摄入任务状态。与文档的 {@link DocumentStatus}（摄入状态机）对应，
 * 但独立存在于后台任务调度域。
 */
public enum IngestionJobStatus {
    /** 已入队，等待 worker 认领 */
    QUEUED,
    /** 正在执行文本切分与向量生成 */
    RUNNING,
    /** 执行失败，已安排退避重试 */
    RETRYING,
    /** 摄入完成，文档已标记为 COMPLETED */
    SUCCEEDED,
    /** 最终失败，不再自动重试 */
    FAILED
}
