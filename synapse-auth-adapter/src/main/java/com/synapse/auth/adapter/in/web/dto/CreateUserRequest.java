package com.synapse.auth.adapter.in.web.dto;

import com.synapse.auth.model.RoleName;

import java.util.Set;

/** 创建用户请求 DTO。密码为明文，由后端哈希后存储。 */
public record CreateUserRequest(String username, String displayName, String password, Set<RoleName> roles) {
}
