package com.synapse.auth.model;

/**
 * 系统预定义角色名。每个角色名对应一条 {@link RoleDefinition} 记录，
 * 定义其拥有的 {@link AuthPermission} 集合。
 */
public enum RoleName {
    /** 系统管理员，拥有全部权限。 */
    ADMIN,
    /** 普通用户，默认拥有 KB_READ、KB_WRITE。 */
    USER
}
