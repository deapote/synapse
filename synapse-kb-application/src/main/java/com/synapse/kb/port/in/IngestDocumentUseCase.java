package com.synapse.kb.port.in;

import com.synapse.kb.model.DocumentId;
import com.synapse.kb.model.KnowledgeBaseId;

import java.io.InputStream;

/**
 * 文档摄入用例。
 *
 * <p>入站端口（Driving Port），负责协调完整的文档处理流程：
 * 解析 → 分块 → 向量化 → 存入向量库 → 保存元数据。
 * 由适配器层（如 Web Controller）调用。
 */
public interface IngestDocumentUseCase {

    /**
     * 摄入文档。
     *
     * @param command 摄入命令
     * @return 新创建的文档 ID
     */
    DocumentId ingest(IngestDocumentCommand command);

    /**
     * 文档摄入命令参数。
     *
     * @param knowledgeBaseId 目标知识库 ID
     * @param fileName        原始文件名
     * @param contentType     MIME 类型，如 application/pdf
     * @param fileSize        文件大小（字节）
     * @param contentHash     文件内容哈希，用于去重
     * @param content         文件内容输入流
     */
    record IngestDocumentCommand(
            KnowledgeBaseId knowledgeBaseId,
            String fileName,
            String contentType,
            long fileSize,
            String contentHash,
            InputStream content
    ) {
    }
}
