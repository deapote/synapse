package com.synapse.auth.model;

import com.synapse.shared.exception.DomainException;

import java.util.EnumSet;
import java.util.Set;

public class RoleDefinition {
    private final RoleName name;
    private Set<AuthPermission> permissions;

    private RoleDefinition(RoleName name, Set<AuthPermission> permissions) {
        this.name = name;
        this.permissions = permissions;
    }

    public static RoleDefinition create(RoleName name, Set<AuthPermission> permissions) {
        if (name == null) {
            throw new DomainException("角色不能为空");
        }
        if (permissions == null || permissions.isEmpty()) {
            throw new DomainException("角色权限不能为空");
        }
        return new RoleDefinition(name, EnumSet.copyOf(permissions));
    }

    public void assignPermissions(Set<AuthPermission> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            throw new DomainException("角色权限不能为空");
        }
        this.permissions = EnumSet.copyOf(permissions);
    }

    public RoleName getName() {
        return name;
    }

    public Set<AuthPermission> getPermissions() {
        return Set.copyOf(permissions);
    }
}
