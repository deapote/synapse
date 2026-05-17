package com.synapse.kb.adapter.out.persistence;

import com.mongodb.client.gridfs.model.GridFSFile;
import com.synapse.kb.model.DocumentId;
import com.synapse.kb.model.KnowledgeBaseId;
import com.synapse.kb.port.out.DocumentContentStorePort;
import com.synapse.shared.exception.DomainException;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.stereotype.Component;

import java.io.InputStream;

@Component
public class GridFsDocumentContentStoreAdapter implements DocumentContentStorePort {

    private final GridFsTemplate gridFsTemplate;

    public GridFsDocumentContentStoreAdapter(GridFsTemplate gridFsTemplate) {
        this.gridFsTemplate = gridFsTemplate;
    }

    @Override
    public String store(KnowledgeBaseId knowledgeBaseId, DocumentId documentId, String fileName,
                        String contentType, InputStream content) {
        org.bson.Document metadata = new org.bson.Document()
                .append("knowledgeBaseId", knowledgeBaseId.value())
                .append("documentId", documentId.value())
                .append("contentType", contentType);
        try (InputStream input = content) {
            ObjectId objectId = gridFsTemplate.store(input, fileName, contentType, metadata);
            return objectId.toHexString();
        } catch (Exception e) {
            throw new DomainException("文档原始内容存储失败", e);
        }
    }

    @Override
    public InputStream open(String contentObjectId) {
        try {
            GridFSFile file = gridFsTemplate.findOne(new Query(Criteria.where("_id").is(new ObjectId(contentObjectId))));
            if (file == null) {
                throw new DomainException("文档原始内容不存在");
            }
            GridFsResource resource = gridFsTemplate.getResource(file);
            return resource.getInputStream();
        } catch (DomainException e) {
            throw e;
        } catch (Exception e) {
            throw new DomainException("读取文档原始内容失败", e);
        }
    }

    @Override
    public void delete(String contentObjectId) {
        if (contentObjectId == null || contentObjectId.isBlank()) {
            return;
        }
        try {
            gridFsTemplate.delete(new Query(Criteria.where("_id").is(new ObjectId(contentObjectId))));
        } catch (Exception e) {
            throw new DomainException("删除文档原始内容失败", e);
        }
    }
}
