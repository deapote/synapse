package com.synapse.auth.adapter.in.web.dto;

import com.synapse.auth.model.AuthPermission;

import java.util.Set;

public record AssignRolePermissionsRequest(Set<AuthPermission> permissions) {
}
