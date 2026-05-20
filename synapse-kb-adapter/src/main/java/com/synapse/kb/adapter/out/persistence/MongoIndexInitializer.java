package com.synapse.kb.adapter.out.persistence;

import com.synapse.kb.adapter.out.persistence.entity.DocumentDocument;
import com.synapse.kb.adapter.out.persistence.entity.KnowledgeBaseDocument;
import com.synapse.kb.adapter.out.persistence.entity.ChunkIndexDocument;
import com.synapse.kb.adapter.out.persistence.entity.ChunkPostingDocument;
import com.synapse.kb.adapter.out.persistence.entity.ChunkCorpusStatsDocument;
import com.synapse.kb.adapter.out.persistence.entity.ChatMessageDocument;
import com.synapse.kb.adapter.out.persistence.entity.ChatSessionDocument;
import com.synapse.kb.adapter.out.persistence.entity.IngestionJobDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.CompoundIndexDefinition;
import org.springframework.data.mongodb.core.index.Index;

@Configuration
public class MongoIndexInitializer {
    private static final Logger log = LoggerFactory.getLogger(MongoIndexInitializer.class);

    @Bean
    ApplicationRunner ensureMongoIndexes(MongoTemplate mongoTemplate) {
        return args -> {
            try {
                mongoTemplate.indexOps(KnowledgeBaseDocument.class)
                        .createIndex(new Index().on("ownerUserId", Sort.Direction.ASC).named("idx_kb_owner"));
                mongoTemplate.indexOps(DocumentDocument.class)
                        .createIndex(new Index().on("knowledgeBaseId", Sort.Direction.ASC).named("idx_doc_kb"));
                mongoTemplate.indexOps(DocumentDocument.class)
                        .createIndex(new Index().on("uploadedAt", Sort.Direction.DESC).named("idx_doc_uploaded_at"));
                mongoTemplate.indexOps(DocumentDocument.class)
                        .createIndex(new CompoundIndexDefinition(
                                org.bson.Document.parse("{'knowledgeBaseId': 1, 'contentHash': 1}"))
                                .unique()
                                .named("uk_kb_content_hash"));
                mongoTemplate.indexOps(ChunkIndexDocument.class)
                        .createIndex(new Index().on("knowledgeBaseId", Sort.Direction.ASC).named("idx_chunk_kb"));
                mongoTemplate.indexOps(ChunkIndexDocument.class)
                        .createIndex(new Index().on("documentId", Sort.Direction.ASC).named("idx_chunk_doc"));
                mongoTemplate.indexOps(ChunkIndexDocument.class)
                        .createIndex(new CompoundIndexDefinition(
                                org.bson.Document.parse("{'knowledgeBaseId': 1, 'tokens': 1}"))
                                .named("idx_chunk_kb_tokens"));
                mongoTemplate.indexOps(ChunkPostingDocument.class)
                        .createIndex(new CompoundIndexDefinition(
                                org.bson.Document.parse("{'knowledgeBaseId': 1, 'token': 1}"))
                                .named("idx_posting_kb_token"));
                mongoTemplate.indexOps(ChunkPostingDocument.class)
                        .createIndex(new CompoundIndexDefinition(
                                org.bson.Document.parse("{'knowledgeBaseId': 1, 'documentId': 1}"))
                                .named("idx_posting_kb_doc"));
                mongoTemplate.indexOps(ChunkCorpusStatsDocument.class)
                        .createIndex(new Index().on("knowledgeBaseId", Sort.Direction.ASC).named("idx_chunk_stats_kb"));
                mongoTemplate.indexOps(IngestionJobDocument.class)
                        .createIndex(new Index().on("documentId", Sort.Direction.ASC).named("idx_ingestion_document"));
                mongoTemplate.indexOps(IngestionJobDocument.class)
                        .createIndex(new Index().on("knowledgeBaseId", Sort.Direction.ASC).named("idx_ingestion_kb"));
                mongoTemplate.indexOps(IngestionJobDocument.class)
                        .createIndex(new CompoundIndexDefinition(
                                org.bson.Document.parse("{'status': 1, 'nextRunAt': 1, 'leaseExpiresAt': 1}"))
                                .named("idx_ingestion_claim"));
                mongoTemplate.indexOps(ChatSessionDocument.class)
                        .createIndex(new CompoundIndexDefinition(
                                org.bson.Document.parse("{'ownerUserId': 1, 'knowledgeBaseId': 1, 'updatedAt': -1}"))
                                .named("idx_chat_session_owner_kb_updated"));
                mongoTemplate.indexOps(ChatMessageDocument.class)
                        .createIndex(new CompoundIndexDefinition(
                                org.bson.Document.parse("{'sessionId': 1, 'sequence': 1}"))
                                .unique()
                                .named("uk_chat_message_session_sequence"));
                mongoTemplate.indexOps(ChatMessageDocument.class)
                        .createIndex(new CompoundIndexDefinition(
                                org.bson.Document.parse("{'ownerUserId': 1, 'knowledgeBaseId': 1, 'createdAt': -1}"))
                                .named("idx_chat_message_owner_kb_created"));
            } catch (DataAccessException e) {
                log.warn("MongoDB 不可用，跳过索引初始化: {}", e.getMessage());
            }
        };
    }
}
