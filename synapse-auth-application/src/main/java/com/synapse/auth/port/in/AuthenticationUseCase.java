package com.synapse.auth.port.in;

import com.synapse.auth.model.AuthPermission;
import com.synapse.auth.model.RoleName;
import com.synapse.auth.model.UserId;

import java.time.Instant;
import java.util.Set;

/**
 * 认证入站端口。定义登录、注销、当前用户查询用例。
 * 实现类由 {@link com.synapse.auth.port.service.AuthApplicationService} 提供。
 */
public interface AuthenticationUseCase {
    LoginResult login(LoginCommand command);

    void logout();

    CurrentUser currentUser();

    record LoginCommand(String username, String password) {
    }

    record LoginResult(String userId, String username, String displayName, Set<RoleName> roles,
                       Set<AuthPermission> permissions) {
    }

    record CurrentUser(UserId id, String username, String displayName, Set<RoleName> roles,
                       Set<AuthPermission> permissions, Instant createdAt) {
    }
}
