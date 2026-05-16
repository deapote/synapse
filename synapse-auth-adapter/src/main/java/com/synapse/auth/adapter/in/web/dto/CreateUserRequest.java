package com.synapse.auth.adapter.in.web.dto;

import com.synapse.auth.model.RoleName;

import java.util.Set;

public record CreateUserRequest(String username, String displayName, String password, Set<RoleName> roles) {
}
