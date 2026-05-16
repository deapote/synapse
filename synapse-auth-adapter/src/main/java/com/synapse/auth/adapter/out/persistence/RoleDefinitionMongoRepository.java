package com.synapse.auth.adapter.out.persistence;

import com.synapse.auth.adapter.out.persistence.entity.RoleDefinitionDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface RoleDefinitionMongoRepository extends MongoRepository<RoleDefinitionDocument, String> {
}
