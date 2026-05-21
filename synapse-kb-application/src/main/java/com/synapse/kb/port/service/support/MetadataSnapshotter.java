package com.synapse.kb.port.service.support;

import com.synapse.kb.model.Document;
import com.synapse.kb.model.DocumentMetadata;

/**
 * 元数据快照辅助类。
 * 将文档时效与业务元数据序列化为审计字符串，或还原为 DocumentMetadata 值对象。
 */
public class MetadataSnapshotter {
    public String snapshotMetadata(Document document) {
        return String.format(
                "sourceType=%s,canonicalKey=%s,versionLabel=%s,effectiveFrom=%s,effectiveTo=%s," +
                "lifecycleStatus=%s,authorityLevel=%d,jurisdiction=%s",
                document.getSourceType(),
                document.getCanonicalKey(),
                document.getVersionLabel(),
                document.getEffectiveFrom(),
                document.getEffectiveTo(),
                document.getLifecycleStatus(),
                document.getAuthorityLevel(),
                document.getJurisdiction()
        );
    }

    public DocumentMetadata toMetadata(Document document) {
        return new DocumentMetadata(
                document.getSourceType(),
                document.getCanonicalKey(),
                document.getVersionLabel(),
                document.getEffectiveFrom(),
                document.getEffectiveTo(),
                document.getSupersedesDocumentId(),
                document.getAuthorityLevel(),
                document.getJurisdiction()
        );
    }
}
