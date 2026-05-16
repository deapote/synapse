package com.synapse.auth.adapter.out.persistence.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Set;

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
