package com.synapse.kb.adapter.out.persistence;

import com.synapse.kb.adapter.out.persistence.entity.DocumentDocument;
import com.synapse.kb.adapter.out.persistence.entity.KnowledgeBaseDocument;
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
                        .ensureIndex(new Index().on("ownerUserId", Sort.Direction.ASC).named("idx_kb_owner"));
                mongoTemplate.indexOps(DocumentDocument.class)
                        .ensureIndex(new Index().on("knowledgeBaseId", Sort.Direction.ASC).named("idx_doc_kb"));
                mongoTemplate.indexOps(DocumentDocument.class)
                        .ensureIndex(new Index().on("uploadedAt", Sort.Direction.DESC).named("idx_doc_uploaded_at"));
                mongoTemplate.indexOps(DocumentDocument.class)
                        .ensureIndex(new CompoundIndexDefinition(
                                org.bson.Document.parse("{'knowledgeBaseId': 1, 'contentHash': 1}"))
                                .unique()
                                .named("uk_kb_content_hash"));
            } catch (DataAccessException e) {
                log.warn("MongoDB 不可用，跳过索引初始化: {}", e.getMessage());
            }
        };
    }
}
