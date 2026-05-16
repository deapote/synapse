package com.synapse.auth.port.in;

import com.synapse.auth.model.AuthPermission;
import com.synapse.auth.model.RoleName;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public interface UserAdminUseCase {
    UserView createUser(CreateUserCommand command);

    List<UserView> listUsers(int page, int size);

    UserView assignRoles(String userId, Set<RoleName> roles);

    UserView setEnabled(String userId, boolean enabled);

    RoleView assignRolePermissions(RoleName roleName, Set<AuthPermission> permissions);

    List<RoleView> listRoles();

    record CreateUserCommand(String username, String displayName, String password, Set<RoleName> roles) {
    }

    record UserView(String id, String username, String displayName, Set<RoleName> roles,
                    boolean enabled, Instant createdAt) {
    }

    record RoleView(RoleName name, Set<AuthPermission> permissions) {
    }
}
