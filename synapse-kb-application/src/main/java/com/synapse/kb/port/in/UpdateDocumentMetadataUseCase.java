package com.synapse.kb.port.in;

import com.synapse.kb.model.Document;
import com.synapse.kb.model.DocumentId;
import com.synapse.kb.model.PatchDocumentMetadata;

/**
 * 更新文档元数据。不重新上传文件，只修改时效等元数据字段。
 * 修改后标记索引为 STALE，由异步任务刷新索引。
 */
public interface UpdateDocumentMetadataUseCase {
    Document updateMetadata(DocumentId id, PatchDocumentMetadata metadata);
}
