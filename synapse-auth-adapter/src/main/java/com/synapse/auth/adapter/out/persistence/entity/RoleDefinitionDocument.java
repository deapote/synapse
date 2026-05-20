package com.synapse.auth.adapter.out.persistence.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Set;

/**
 * MongoDB 角色定义持久化实体。对应 {@code auth_roles} 集合。
 * 以角色名为 _id，存储该角色拥有的权限集合。
 */
@Document(collection = "auth_roles")
public class RoleDefinitionDocument {
    @Id
    private String name;
    private Set<String> permissions;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Set<String> getPermissions() { return permissions; }
    public void setPermissions(Set<String> permissions) { this.permissions = permissions; }
}
