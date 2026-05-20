package com.synapse.auth.adapter.in.web.dto;

import com.synapse.auth.model.AuthPermission;
import com.synapse.auth.model.RoleName;

import java.util.Set;

/** 角色信息响应 DTO。 */
public record RoleResponse(RoleName name, Set<AuthPermission> permissions) {
}
