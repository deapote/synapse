package com.synapse.kb.port.in;

import com.synapse.kb.model.Document;
import com.synapse.kb.model.DocumentId;

/**
 * 重建文档索引用例。
 * 标记文档索引为陈旧并触发后台索引刷新任务。
 */
public interface ReindexDocumentUseCase {

    Document reindex(DocumentId id);
}
