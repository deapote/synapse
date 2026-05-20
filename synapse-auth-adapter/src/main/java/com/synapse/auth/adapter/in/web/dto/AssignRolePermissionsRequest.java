package com.synapse.auth.adapter.in.web.dto;

import com.synapse.auth.model.AuthPermission;

import java.util.Set;

/** 分配角色权限请求 DTO。 */
public record AssignRolePermissionsRequest(Set<AuthPermission> permissions) {
}
