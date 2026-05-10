package com.synapse.kb.port.in;

import com.synapse.kb.model.DocumentId;

/**
 * 删除文档用例。
 *
 * <p>入站端口（Driving Port），删除文档元数据并级联清理向量库中的相关向量。
 */
public interface DeleteDocumentUseCase {

    /**
     * 删除指定文档。
     *
     * @param id 文档 ID
     */
    void delete(DocumentId id);
}
