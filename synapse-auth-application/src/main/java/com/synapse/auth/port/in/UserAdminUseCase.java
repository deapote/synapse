package com.synapse.auth.port.in;

import com.synapse.auth.model.AuthPermission;
import com.synapse.auth.model.RoleName;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * 用户管理入站端口。定义创建用户、角色分配、权限管理等管理用例。
 * 调用方必须持有 {@code AUTH_ADMIN} 权限（由 Web 层校验）。
 */
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
