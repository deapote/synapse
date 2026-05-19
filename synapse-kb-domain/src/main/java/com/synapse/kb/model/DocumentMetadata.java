package com.synapse.kb.model;

import java.time.LocalDate;

/**
 * 文档时效元数据值对象。上传或更新时携带，所有字段均可空，由 domain 层提供默认值。
 */
public record DocumentMetadata(
        DocumentSourceType sourceType,
        String canonicalKey,
        String versionLabel,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        String supersedesDocumentId,
        Integer authorityLevel,
        String jurisdiction
) {
    public DocumentMetadata() {
        this(null, null, null, null, null, null, null, null);
    }
}
