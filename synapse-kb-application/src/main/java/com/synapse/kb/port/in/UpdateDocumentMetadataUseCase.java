package com.synapse.kb.port.in;

import com.synapse.kb.model.Document;
import com.synapse.kb.model.DocumentId;
import com.synapse.kb.model.DocumentMetadata;

/**
 * 更新文档元数据。不重新上传文件，只修改时效等元数据字段。
 * 实现方必须同步更新 Milvus 和 Mongo BM25 索引中的对应 scalar 字段。
 */
public interface UpdateDocumentMetadataUseCase {
    Document updateMetadata(DocumentId id, DocumentMetadata metadata);
}
