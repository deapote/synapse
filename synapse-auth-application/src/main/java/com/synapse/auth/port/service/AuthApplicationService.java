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

    @Override
    public void logout() {
        loginSession.logout();
    }

    @Override
    public CurrentUser currentUser() {
        UserId userId = loginSession.currentUserId()
                .orElseThrow(() -> new DomainException("用户未登录"));
        UserAccount user = userRepository.findById(userId)
                .orElseThrow(() -> new DomainException("用户不存在"));
        return new CurrentUser(user.getId(), user.getUsername(), user.getDisplayName(),
                user.getRoles(), permissionsOf(user), user.getCreatedAt());
    }

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

    @Override
    public UserView assignRoles(String userId, Set<RoleName> roles) {
        UserAccount user = userRepository.findById(new UserId(userId))
                .orElseThrow(() -> new DomainException("用户不存在"));
        user.assignRoles(roles);
        return toView(userRepository.save(user));
    }

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
