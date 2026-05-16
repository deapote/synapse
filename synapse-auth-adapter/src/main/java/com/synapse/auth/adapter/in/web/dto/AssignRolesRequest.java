package com.synapse.auth.adapter.in.web.dto;

import com.synapse.auth.model.RoleName;

import java.util.Set;

public record AssignRolesRequest(Set<RoleName> roles) {
}
