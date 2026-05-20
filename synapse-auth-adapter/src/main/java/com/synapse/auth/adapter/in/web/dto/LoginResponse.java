package com.synapse.auth.adapter.in.web.dto;

import com.synapse.auth.model.AuthPermission;
import com.synapse.auth.model.RoleName;

import java.util.Set;

/** 登录响应 DTO。包含用户基本信息和 Sa-Token 令牌。 */
public record LoginResponse(String id, String username, String displayName, Set<RoleName> roles,
                            Set<AuthPermission> permissions, String tokenName, String tokenValue) {
}
