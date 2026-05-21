package com.synapse.kb.port.out;

import com.synapse.kb.model.DocumentId;
import com.synapse.kb.model.KnowledgeBaseId;

import java.io.InputStream;

/**
 * 文档内容存储端口，由对象存储适配器实现。
 * 负责原始文件的上传、读取与删除。
 */
public interface DocumentContentStorePort {

    String store(KnowledgeBaseId knowledgeBaseId, DocumentId documentId, String fileName,
                 String contentType, InputStream content);

    InputStream open(String contentObjectId);

    void delete(String contentObjectId);
}
