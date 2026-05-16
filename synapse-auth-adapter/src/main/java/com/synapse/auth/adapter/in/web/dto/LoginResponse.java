package com.synapse.auth.adapter.in.web.dto;

import com.synapse.auth.model.AuthPermission;
import com.synapse.auth.model.RoleName;

import java.util.Set;

public record LoginResponse(String id, String username, String displayName, Set<RoleName> roles,
                            Set<AuthPermission> permissions, String tokenName, String tokenValue) {
}
