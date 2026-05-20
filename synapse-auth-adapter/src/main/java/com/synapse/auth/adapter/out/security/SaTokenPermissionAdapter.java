package com.synapse.auth.adapter.out.security;

import cn.dev33.satoken.stp.StpInterface;
import com.synapse.auth.model.RoleName;
import com.synapse.auth.model.UserId;
import com.synapse.auth.repository.RoleDefinitionRepository;
import com.synapse.auth.repository.UserAccountRepository;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Sa-Token 权限数据源适配器。根据用户 ID 从仓储查询角色和权限，
 * 供 Sa-Token 的注解鉴权（如 {@code @SaCheckPermission}）使用。
 * 只返回已启用用户的权限，禁用用户视为无权限。
 */
@Component
public class SaTokenPermissionAdapter implements StpInterface {
    private final UserAccountRepository userRepository;
    private final RoleDefinitionRepository roleRepository;

    public SaTokenPermissionAdapter(UserAccountRepository userRepository,
                                    RoleDefinitionRepository roleRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
    }

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        return userRepository.findById(new UserId(String.valueOf(loginId)))
                .filter(user -> user.isEnabled())
                .stream()
                .flatMap(user -> user.getRoles().stream())
                .flatMap(role -> roleRepository.findByName(role).stream())
                .flatMap(role -> role.getPermissions().stream())
                .map(Enum::name)
                .distinct()
                .toList();
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        return userRepository.findById(new UserId(String.valueOf(loginId)))
                .filter(user -> user.isEnabled())
                .stream()
                .flatMap(user -> user.getRoles().stream())
                .map(RoleName::name)
                .toList();
    }
}
