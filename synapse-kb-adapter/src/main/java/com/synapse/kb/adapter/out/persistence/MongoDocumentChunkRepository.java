package com.synapse.kb.adapter.out.persistence;

import com.synapse.kb.adapter.out.persistence.entity.DocumentChunkDocument;
import com.synapse.kb.model.DocumentChunk;
import com.synapse.kb.model.DocumentId;
import com.synapse.kb.repository.DocumentChunkRepository;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 文档分块 MongoDB 仓储适配器。
 */
@Component
public class MongoDocumentChunkRepository implements DocumentChunkRepository {

    private final DocumentChunkMongoRepository chunkMongoRepository;

    public MongoDocumentChunkRepository(DocumentChunkMongoRepository chunkMongoRepository) {
        this.chunkMongoRepository = chunkMongoRepository;
    }

    @Override
    public void save(DocumentId documentId, List<DocumentChunk> chunks) {
        chunkMongoRepository.deleteByDocumentId(documentId.value());
        List<DocumentChunkDocument> documents = chunks.stream()
                .map(chunk -> {
                    DocumentChunkDocument doc = new DocumentChunkDocument();
                    doc.setDocumentId(documentId.value());
                    doc.setChunkIndex(chunk.index());
                    doc.setText(chunk.text());
                    doc.setStartPosition(chunk.startPosition());
                    doc.setEndPosition(chunk.endPosition());
                    return doc;
                })
                .toList();
        chunkMongoRepository.saveAll(documents);
    }

    @Override
    public List<DocumentChunk> findByDocumentId(DocumentId documentId) {
        return chunkMongoRepository.findByDocumentId(documentId.value()).stream()
                .map(doc -> new DocumentChunk(
                        doc.getChunkIndex(),
                        doc.getText(),
                        doc.getStartPosition(),
                        doc.getEndPosition()
                ))
                .toList();
    }

    @Override
    public void deleteByDocumentId(DocumentId documentId) {
        chunkMongoRepository.deleteByDocumentId(documentId.value());
    }
}
