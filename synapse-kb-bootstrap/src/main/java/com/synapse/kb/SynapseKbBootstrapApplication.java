package com.synapse.kb;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.mongo.MongoReactiveRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoReactiveAutoConfiguration;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@EnableMongoRepositories
@SpringBootApplication(exclude = {
        MongoReactiveAutoConfiguration.class,
        MongoReactiveRepositoriesAutoConfiguration.class
})
public class SynapseKbBootstrapApplication {

    public static void main(String[] args) {
        SpringApplication.run(SynapseKbBootstrapApplication.class, args);
    }

}
