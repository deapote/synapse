package com.synapse.auth.repository;

import com.synapse.auth.model.RoleDefinition;
import com.synapse.auth.model.RoleName;

import java.util.List;
import java.util.Optional;

/** 角色定义仓储接口。领域层只定义契约，持久化由 adapter 实现。 */
public interface RoleDefinitionRepository {
    RoleDefinition save(RoleDefinition role);

    Optional<RoleDefinition> findByName(RoleName name);

    List<RoleDefinition> findAll();
}
