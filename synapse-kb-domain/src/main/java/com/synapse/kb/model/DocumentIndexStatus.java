package com.synapse.kb.model;

/**
 * 文档索引同步状态。与摄入状态机（DocumentStatus）独立。
 */
public enum DocumentIndexStatus {
    /** 索引与 Mongo 权威状态一致 */
    SYNCED,
    /** 权威状态已变更，索引待刷新 */
    STALE,
    /** 索引刷新中 */
    REFRESHING,
    /** 索引刷新失败 */
    FAILED
}
