package com.synapse.auth.model;

import com.synapse.shared.exception.DomainException;

import java.util.EnumSet;
import java.util.Set;

/**
 * 角色定义聚合根。角色名唯一，作为权限集合的载体。
 * ADMIN 与 USER 为系统预定义角色，其权限在初始化时写入。
 */
public class RoleDefinition {
    private final RoleName name;
    private Set<AuthPermission> permissions;

    private RoleDefinition(RoleName name, Set<AuthPermission> permissions) {
        this.name = name;
        this.permissions = permissions;
    }

    /** 创建角色定义。权限集合不能为空。 */
    public static RoleDefinition create(RoleName name, Set<AuthPermission> permissions) {
        if (name == null) {
            throw new DomainException("角色不能为空");
        }
        if (permissions == null || permissions.isEmpty()) {
            throw new DomainException("角色权限不能为空");
        }
        return new RoleDefinition(name, EnumSet.copyOf(permissions));
    }

    /** 重新分配权限集合。不能为空。 */
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
