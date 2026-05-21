package com.synapse.kb.port.in;

import com.synapse.kb.model.Document;
import com.synapse.kb.model.DocumentId;

/**
 * 重试文档摄入用例。
 * 对处于 FAILED 状态的文档清理旧索引后重新提交摄入任务。
 */
public interface RetryDocumentIngestionUseCase {

    Document retry(DocumentId id);
}
