package com.synapse.auth.adapter.in.web.dto;

import com.synapse.auth.model.RoleName;

import java.util.Set;

/** 分配角色请求 DTO。 */
public record AssignRolesRequest(Set<RoleName> roles) {
}
