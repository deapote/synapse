package com.synapse.auth.port.in;

import com.synapse.auth.model.AuthPermission;
import com.synapse.auth.model.RoleName;
import com.synapse.auth.model.UserId;

import java.time.Instant;
import java.util.Set;

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
