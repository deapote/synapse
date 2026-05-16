package com.synapse.auth.repository;

import com.synapse.auth.model.RoleDefinition;
import com.synapse.auth.model.RoleName;

import java.util.List;
import java.util.Optional;

public interface RoleDefinitionRepository {
    RoleDefinition save(RoleDefinition role);

    Optional<RoleDefinition> findByName(RoleName name);

    List<RoleDefinition> findAll();
}
