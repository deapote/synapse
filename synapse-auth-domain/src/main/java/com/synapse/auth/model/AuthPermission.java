package com.synapse.auth.model;

/**
 * 系统权限枚举。权限通过 {@link RoleDefinition} 授予角色，角色再授予用户。
 * 鉴权时检查用户拥有的角色是否包含所需权限。
 */
public enum AuthPermission {
    /** 知识库读权限。 */
    KB_READ,
    /** 知识库写权限（上传文档、修改 metadata）。 */
    KB_WRITE,
    /** 知识库删除权限。 */
    KB_DELETE,
    /** 用户管理权限（创建用户、分配角色、修改角色权限）。 */
    AUTH_ADMIN
}
