package com.synapse.kb.adapter.out.persistence;

import com.synapse.kb.adapter.out.persistence.entity.ChunkCorpusStatsDocument;
import com.synapse.kb.adapter.out.persistence.entity.ChunkIndexDocument;
import com.synapse.kb.adapter.out.persistence.entity.ChunkPostingDocument;
import com.synapse.kb.model.DocumentChunk;
import com.synapse.kb.model.DocumentId;
import com.synapse.kb.model.DocumentMetadata;
import com.synapse.kb.model.KnowledgeBaseId;
import com.synapse.shared.exception.DomainException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class MongoChunkIndexWriter {

    private final ChunkIndexMongoRepository repository;
    private final MongoTemplate mongoTemplate;
    private final ChunkIndexDocumentMapper mapper = new ChunkIndexDocumentMapper();

    MongoChunkIndexWriter(ChunkIndexMongoRepository repository, MongoTemplate mongoTemplate) {
        this.repository = repository;
        this.mongoTemplate = mongoTemplate;
    }

    void store(KnowledgeBaseId knowledgeBaseId, DocumentId documentId, String documentName,
               List<DocumentChunk> chunks, DocumentMetadata metadata) {
        try {
            deleteByDocumentId(knowledgeBaseId, documentId);
            List<ChunkIndexDocument> chunkDocuments = chunks.stream()
                    .map(chunk -> mapper.toDocument(knowledgeBaseId, documentId, documentName, chunk, metadata))
                    .toList();
            repository.saveAll(chunkDocuments);
            List<ChunkPostingDocument> postings = new ArrayList<>();
            long tokenCount = 0;
            for (ChunkIndexDocument chunk : chunkDocuments) {
                tokenCount += chunk.getTokenCount();
                for (Map.Entry<String, Integer> entry : chunk.getTermFrequencies().entrySet()) {
                    postings.add(mapper.toPosting(chunk, entry.getKey(), entry.getValue()));
                }
            }
            if (!postings.isEmpty()) {
                mongoTemplate.insert(postings, ChunkPostingDocument.class);
            }
            mongoTemplate.upsert(
                    new Query(Criteria.where("_id").is(knowledgeBaseId.value())),
                    new Update()
                            .inc("totalChunks", chunkDocuments.size())
                            .inc("totalTokenCount", tokenCount),
                    ChunkCorpusStatsDocument.class
            );
        } catch (Exception e) {
            throw new DomainException("关键词索引写入失败", e);
        }
    }

    void refreshStore(KnowledgeBaseId knowledgeBaseId, DocumentId documentId, String documentName,
                      List<DocumentChunk> chunks, DocumentMetadata metadata) {
        try {
            List<ChunkIndexDocument> oldChunks = repository.findByKnowledgeBaseIdAndDocumentId(
                    knowledgeBaseId.value(), documentId.value());
            long oldTotalTokenCount = oldChunks.stream().mapToInt(ChunkIndexDocument::getTokenCount).sum();

            java.util.Set<Integer> newChunkIndices = chunks.stream()
                    .map(DocumentChunk::index)
                    .collect(java.util.stream.Collectors.toSet());
            List<ChunkIndexDocument> orphanedOldChunks = oldChunks.stream()
                    .filter(c -> !newChunkIndices.contains(c.getChunkIndex()))
                    .toList();

            List<ChunkIndexDocument> chunkDocuments = chunks.stream()
                    .map(chunk -> mapper.toDocument(knowledgeBaseId, documentId, documentName, chunk, metadata))
                    .toList();
            repository.saveAll(chunkDocuments);

            for (ChunkIndexDocument orphan : orphanedOldChunks) {
                repository.deleteById(orphan.getId());
            }

            List<ChunkPostingDocument> newPostings = new ArrayList<>();
            long newTotalTokenCount = 0;
            for (ChunkIndexDocument chunk : chunkDocuments) {
                newTotalTokenCount += chunk.getTokenCount();
                for (Map.Entry<String, Integer> entry : chunk.getTermFrequencies().entrySet()) {
                    newPostings.add(mapper.toPosting(chunk, entry.getKey(), entry.getValue()));
                }
            }

            List<ChunkPostingDocument> oldPostings = mongoTemplate.find(
                    new Query(Criteria.where("knowledgeBaseId").is(knowledgeBaseId.value())
                            .and("documentId").is(documentId.value())),
                    ChunkPostingDocument.class);
            java.util.Set<String> oldPostingIds = oldPostings.stream()
                    .map(ChunkPostingDocument::getId)
                    .collect(java.util.stream.Collectors.toSet());

            for (ChunkPostingDocument posting : newPostings) {
                mongoTemplate.save(posting);
            }

            java.util.Set<String> newPostingIds = newPostings.stream()
                    .map(ChunkPostingDocument::getId)
                    .collect(java.util.stream.Collectors.toSet());
            List<String> postingIdsToDelete = oldPostingIds.stream()
                    .filter(id -> !newPostingIds.contains(id))
                    .toList();
            if (!postingIdsToDelete.isEmpty()) {
                mongoTemplate.remove(
                        new Query(Criteria.where("_id").in(postingIdsToDelete)),
                        ChunkPostingDocument.class);
            }

            mongoTemplate.upsert(
                    new Query(Criteria.where("_id").is(knowledgeBaseId.value())),
                    new Update()
                            .inc("totalChunks", chunkDocuments.size() - oldChunks.size())
                            .inc("totalTokenCount", newTotalTokenCount - oldTotalTokenCount),
                    ChunkCorpusStatsDocument.class
            );
        } catch (Exception e) {
            throw new DomainException("关键词索引刷新失败", e);
        }
    }

    void deleteByDocumentId(KnowledgeBaseId knowledgeBaseId, DocumentId documentId) {
        try {
            List<ChunkIndexDocument> chunks = repository.findByKnowledgeBaseIdAndDocumentId(
                    knowledgeBaseId.value(), documentId.value());
            long tokenCount = chunks.stream().mapToInt(ChunkIndexDocument::getTokenCount).sum();
            repository.deleteByKnowledgeBaseIdAndDocumentId(knowledgeBaseId.value(), documentId.value());
            mongoTemplate.remove(new Query(Criteria.where("knowledgeBaseId").is(knowledgeBaseId.value())
                    .and("documentId").is(documentId.value())), ChunkPostingDocument.class);
            if (!chunks.isEmpty()) {
                mongoTemplate.updateFirst(
                        new Query(Criteria.where("_id").is(knowledgeBaseId.value())),
                        new Update()
                                .inc("totalChunks", -chunks.size())
                                .inc("totalTokenCount", -tokenCount),
                        ChunkCorpusStatsDocument.class
                );
            }
        } catch (Exception e) {
            throw new DomainException("关键词索引删除失败", e);
        }
    }
}
