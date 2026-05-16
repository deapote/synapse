package com.synapse.auth.adapter.out.persistence;

import com.synapse.auth.adapter.out.persistence.entity.UserAccountDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

@Configuration
public class AuthMongoIndexInitializer {
    private static final Logger log = LoggerFactory.getLogger(AuthMongoIndexInitializer.class);

    @Bean
    ApplicationRunner ensureAuthMongoIndexes(MongoTemplate mongoTemplate) {
        return args -> {
            try {
                mongoTemplate.indexOps(UserAccountDocument.class)
                        .ensureIndex(new Index().on("username", Sort.Direction.ASC).unique().named("uk_auth_username"));
            } catch (DataAccessException e) {
                log.warn("MongoDB 不可用，跳过认证索引初始化: {}", e.getMessage());
            }
        };
    }
}
