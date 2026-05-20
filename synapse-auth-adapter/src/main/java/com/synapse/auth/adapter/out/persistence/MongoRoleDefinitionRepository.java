package com.synapse.auth.adapter.out.persistence;

import com.synapse.auth.adapter.out.persistence.entity.RoleDefinitionDocument;
import com.synapse.auth.model.AuthPermission;
import com.synapse.auth.model.RoleDefinition;
import com.synapse.auth.model.RoleName;
import com.synapse.auth.repository.RoleDefinitionRepository;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 角色定义仓储适配器。将领域 {@link RoleDefinition} 与 MongoDB {@link RoleDefinitionDocument} 映射。
 */
@Component
public class MongoRoleDefinitionRepository implements RoleDefinitionRepository {
    private final RoleDefinitionMongoRepository mongoRepository;

    public MongoRoleDefinitionRepository(RoleDefinitionMongoRepository mongoRepository) {
        this.mongoRepository = mongoRepository;
    }

    @Override
    public RoleDefinition save(RoleDefinition role) {
        return toEntity(mongoRepository.save(toDocument(role)));
    }

    @Override
    public Optional<RoleDefinition> findByName(RoleName name) {
        return mongoRepository.findById(name.name()).map(this::toEntity);
    }

    @Override
    public List<RoleDefinition> findAll() {
        return mongoRepository.findAll().stream().map(this::toEntity).toList();
    }

    private RoleDefinitionDocument toDocument(RoleDefinition role) {
        RoleDefinitionDocument doc = new RoleDefinitionDocument();
        doc.setName(role.getName().name());
        doc.setPermissions(role.getPermissions().stream().map(Enum::name).collect(Collectors.toSet()));
        return doc;
    }

    private RoleDefinition toEntity(RoleDefinitionDocument doc) {
        Set<AuthPermission> permissions = doc.getPermissions() == null || doc.getPermissions().isEmpty()
                ? EnumSet.noneOf(AuthPermission.class)
                : doc.getPermissions().stream().map(AuthPermission::valueOf)
                        .collect(Collectors.toCollection(() -> EnumSet.noneOf(AuthPermission.class)));
        return RoleDefinition.create(RoleName.valueOf(doc.getName()), permissions);
    }
}
