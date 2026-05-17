package com.synapse.kb.adapter.out.persistence;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.convert.MongoConverter;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;

@Configuration
public class MongoGridFsConfig {

    @Bean
    GridFsTemplate gridFsTemplate(MongoDatabaseFactory mongoDatabaseFactory,
                                  MongoConverter mongoConverter,
                                  @Value("${synapse.ingestion.gridfs.bucket:synapse_document_content}") String bucket) {
        return new GridFsTemplate(mongoDatabaseFactory, mongoConverter, bucket);
    }
}
