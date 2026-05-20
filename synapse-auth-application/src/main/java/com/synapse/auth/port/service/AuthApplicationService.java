package com.synapse.auth.port.service;

import com.synapse.auth.model.AuthPermission;
import com.synapse.auth.model.RoleDefinition;
import com.synapse.auth.model.RoleName;
import com.synapse.auth.model.UserAccount;
import com.synapse.auth.model.UserId;
import com.synapse.auth.port.in.AuthenticationUseCase;
import com.synapse.auth.port.in.UserAdminUseCase;
import com.synapse.auth.port.out.LoginSessionPort;
import com.synapse.auth.port.out.PasswordHasherPort;
import com.synapse.auth.repository.RoleDefinitionRepository;
import com.synapse.auth.repository.UserAccountRepository;
import com.synapse.shared.exception.DomainException;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 认证与授权用例编排器。
 *
 * <p>应用层只通过端口依赖外部能力：
 * {@link PasswordHasherPort} 负责密码哈希校验，{@link LoginSessionPort} 负责登录会话抽象。
 * 不直接依赖 Web/Mongo/Sa-Token/BCrypt 实现。</p>
 *
 * <p>管理接口（createUser、assignRoles、setEnabled、assignRolePermissions）必须校验
 * {@code AUTH_ADMIN} 权限，该检查由 Web 层通过 {@link com.synapse.kb.port.out.AccessControlPort}
 * 在调用前完成。</p>
 */
public class AuthApplicationService implements AuthenticationUseCase, UserAdminUseCase {
    private final UserAccountRepository userRepository;
    private final RoleDefinitionRepository roleRepository;
    private final PasswordHasherPort passwordHasher;
    private final LoginSessionPort loginSession;

    public AuthApplicationService(UserAccountRepository userRepository,
                                  RoleDefinitionRepository roleRepository,
                                  PasswordHasherPort passwordHasher,
                                  LoginSessionPort loginSession) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordHasher = passwordHasher;
        this.loginSession = loginSession;
    }

    /**
     * 登录认证。查找用户后通过 {@link PasswordHasherPort} 校验密码，
     * 成功后创建登录会话。不区分"用户不存在"与"密码错误"，统一返回相同错误信息防止枚举。
     */
    @Override
    public LoginResult login(LoginCommand command) {
        if (command.username() == null || command.username().isBlank()) {
            throw new DomainException("用户名不能为空");
        }
        if (command.password() == null || command.password().isBlank()) {
            throw new DomainException("密码不能为空");
        }
        UserAccount user = userRepository.findByUsername(command.username().trim())
                .orElseThrow(() -> new DomainException("用户名或密码错误"));
        if (!user.isEnabled() || !passwordHasher.matches(command.password(), user.getPasswordHash())) {
            throw new DomainException("用户名或密码错误");
        }
        loginSession.login(user.getId());
        return new LoginResult(user.getId().value(), user.getUsername(), user.getDisplayName(),
                user.getRoles(), permissionsOf(user));
    }

    /** 注销当前登录会话。 */
    @Override
    public void logout() {
        loginSession.logout();
    }

    /** 获取当前登录用户信息。未登录时抛出 DomainException。 */
    @Override
    public CurrentUser currentUser() {
        UserId userId = loginSession.currentUserId()
                .orElseThrow(() -> new DomainException("用户未登录"));
        UserAccount user = userRepository.findById(userId)
                .orElseThrow(() -> new DomainException("用户不存在"));
        return new CurrentUser(user.getId(), user.getUsername(), user.getDisplayName(),
                user.getRoles(), permissionsOf(user), user.getCreatedAt());
    }

    /**
     * 创建用户。密码明文通过 {@link PasswordHasherPort} 哈希后存入。
     * 角色为空时默认赋予 {@code USER}。
     */
    @Override
    public UserView createUser(CreateUserCommand command) {
        String username = command.username() == null ? null : command.username().trim();
        if (username == null || username.isBlank()) {
            throw new DomainException("用户名不能为空");
        }
        if (command.password() == null || command.password().length() < 8) {
            throw new DomainException("密码长度至少8位");
        }
        if (userRepository.existsByUsername(username)) {
            throw new DomainException("用户名已存在");
        }
        Set<RoleName> roles = command.roles() == null || command.roles().isEmpty()
                ? EnumSet.of(RoleName.USER)
                : EnumSet.copyOf(command.roles());
        UserAccount user = UserAccount.create(username, command.displayName(),
                passwordHasher.hash(command.password()), roles);
        return toView(userRepository.save(user));
    }

    @Override
    public List<UserView> listUsers(int page, int size) {
        return userRepository.findAll(page, size).stream().map(this::toView).toList();
    }

    /** 重新分配用户角色。 */
    @Override
    public UserView assignRoles(String userId, Set<RoleName> roles) {
        UserAccount user = userRepository.findById(new UserId(userId))
                .orElseThrow(() -> new DomainException("用户不存在"));
        user.assignRoles(roles);
        return toView(userRepository.save(user));
    }

    /** 启用或禁用用户账号。 */
    @Override
    public UserView setEnabled(String userId, boolean enabled) {
        UserAccount user = userRepository.findById(new UserId(userId))
                .orElseThrow(() -> new DomainException("用户不存在"));
        if (enabled) {
            user.enable();
        } else {
            user.disable();
        }
        return toView(userRepository.save(user));
    }

    /** 分配角色权限。角色不存在时自动创建。 */
    @Override
    public RoleView assignRolePermissions(RoleName roleName, Set<AuthPermission> permissions) {
        RoleDefinition role = roleRepository.findByName(roleName)
                .orElse(RoleDefinition.create(roleName, permissions));
        role.assignPermissions(permissions);
        RoleDefinition saved = roleRepository.save(role);
        return new RoleView(saved.getName(), saved.getPermissions());
    }

    @Override
    public List<RoleView> listRoles() {
        return roleRepository.findAll().stream()
                .map(role -> new RoleView(role.getName(), role.getPermissions()))
                .toList();
    }

    private UserView toView(UserAccount user) {
        return new UserView(user.getId().value(), user.getUsername(), user.getDisplayName(),
                user.getRoles(), user.isEnabled(), user.getCreatedAt());
    }

    private Set<AuthPermission> permissionsOf(UserAccount user) {
        return user.getRoles().stream()
                .flatMap(role -> roleRepository.findByName(role).stream())
                .flatMap(role -> role.getPermissions().stream())
                .collect(Collectors.toUnmodifiableSet());
    }
}
