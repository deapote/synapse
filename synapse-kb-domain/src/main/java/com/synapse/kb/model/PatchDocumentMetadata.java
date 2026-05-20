package com.synapse.kb.model;

import java.time.LocalDate;

/**
 * 文档 metadata 在线补丁。使用 PatchValue 区分：
 * - unset: 不传，保持原值
 * - set: 覆盖为指定值
 * - clear: 清空为 null
 */
public record PatchDocumentMetadata(
        PatchValue<DocumentSourceType> sourceType,
        PatchValue<String> canonicalKey,
        PatchValue<String> versionLabel,
        PatchValue<LocalDate> effectiveFrom,
        PatchValue<LocalDate> effectiveTo,
        PatchValue<Integer> authorityLevel,
        PatchValue<String> jurisdiction
) {
    public PatchDocumentMetadata {
        sourceType = sourceType != null ? sourceType : PatchValue.unset();
        canonicalKey = canonicalKey != null ? canonicalKey : PatchValue.unset();
        versionLabel = versionLabel != null ? versionLabel : PatchValue.unset();
        effectiveFrom = effectiveFrom != null ? effectiveFrom : PatchValue.unset();
        effectiveTo = effectiveTo != null ? effectiveTo : PatchValue.unset();
        authorityLevel = authorityLevel != null ? authorityLevel : PatchValue.unset();
        jurisdiction = jurisdiction != null ? jurisdiction : PatchValue.unset();
    }

    public PatchDocumentMetadata() {
        this(PatchValue.unset(), PatchValue.unset(), PatchValue.unset(),
             PatchValue.unset(), PatchValue.unset(), PatchValue.unset(), PatchValue.unset());
    }
}
