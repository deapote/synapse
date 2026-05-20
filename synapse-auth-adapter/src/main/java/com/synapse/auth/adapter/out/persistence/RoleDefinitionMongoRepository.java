package com.synapse.auth.adapter.out.persistence;

import com.synapse.auth.adapter.out.persistence.entity.RoleDefinitionDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

/** Spring Data MongoDB 角色定义仓储接口。 */
public interface RoleDefinitionMongoRepository extends MongoRepository<RoleDefinitionDocument, String> {
}
