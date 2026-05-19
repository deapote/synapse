package com.synapse.kb.port.in;

import com.synapse.kb.model.DocumentId;
import com.synapse.kb.model.DocumentMetadata;
import com.synapse.kb.model.KnowledgeBaseId;

import java.io.InputStream;

/**
 * 文档摄入入站端口。实现返回后，文档仍可能处于后台处理状态。
 */
public interface IngestDocumentUseCase {

    DocumentId ingest(IngestDocumentCommand command);

    record IngestDocumentCommand(
            KnowledgeBaseId knowledgeBaseId,
            String fileName,
            String contentType,
            long fileSize,
            String contentHash,
            InputStream content,
            DocumentMetadata metadata
    ) {
        public IngestDocumentCommand {
            if (metadata == null) {
                metadata = new DocumentMetadata();
            }
        }

        public IngestDocumentCommand(KnowledgeBaseId knowledgeBaseId, String fileName, String contentType,
                                     long fileSize, String contentHash, InputStream content) {
            this(knowledgeBaseId, fileName, contentType, fileSize, contentHash, content, new DocumentMetadata());
        }
    }
}
