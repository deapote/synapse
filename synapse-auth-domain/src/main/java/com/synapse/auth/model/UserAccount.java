package com.synapse.auth.model;

import com.synapse.shared.exception.DomainException;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

/**
 * 用户账号聚合根。
 *
 * <p>领域层不依赖 Spring、Sa-Token、BCrypt。密码永远只保存哈希，不保存明文。</p>
 * <p>账号状态由 {@code enabled} 控制；角色集合不能为空，默认至少包含 {@code USER}。</p>
 */
public class UserAccount {
    private final UserId id;
    private String username;
    private String displayName;
    private String passwordHash;
    private Set<RoleName> roles;
    private boolean enabled;
    private final Instant createdAt;

    private UserAccount(UserId id, String username, String displayName, String passwordHash,
                        Set<RoleName> roles, boolean enabled, Instant createdAt) {
        this.id = id;
        this.username = username;
        this.displayName = displayName;
        this.passwordHash = passwordHash;
        this.roles = roles;
        this.enabled = enabled;
        this.createdAt = createdAt;
    }

    /**
     * 创建新用户。角色为空时默认赋予 {@code USER}。
     * {@code passwordHash} 必须是已哈希值，不能是明文密码。
     */
    public static UserAccount create(String username, String displayName, String passwordHash, Set<RoleName> roles) {
        validateUsername(username);
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new DomainException("密码哈希不能为空");
        }
        Set<RoleName> safeRoles = roles == null || roles.isEmpty()
                ? EnumSet.of(RoleName.USER)
                : EnumSet.copyOf(roles);
        return new UserAccount(UserId.generate(), username.trim(), displayName, passwordHash, safeRoles, true, Instant.now());
    }

    /**
     * 仅供仓储层重建聚合根使用。
     */
    public static UserAccount reconstruct(UserId id, String username, String displayName, String passwordHash,
                                          Set<RoleName> roles, boolean enabled, Instant createdAt) {
        return new UserAccount(id, username, displayName, passwordHash,
                roles == null || roles.isEmpty() ? EnumSet.noneOf(RoleName.class) : EnumSet.copyOf(roles),
                enabled, createdAt);
    }

    /**
     * 更新密码哈希。入参必须是已哈希值。
     */
    public void changePasswordHash(String passwordHash) {
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new DomainException("密码哈希不能为空");
        }
        this.passwordHash = passwordHash;
    }

    /**
     * 重新分配角色集合。不能为空。
     */
    public void assignRoles(Set<RoleName> roles) {
        if (roles == null || roles.isEmpty()) {
            throw new DomainException("用户至少需要一个角色");
        }
        this.roles = EnumSet.copyOf(roles);
    }

    /**
     * 启用账号。
     */
    public void enable() {
        this.enabled = true;
    }

    /**
     * 禁用账号。禁用后无法登录，但不删除数据。
     */
    public void disable() {
        this.enabled = false;
    }

    /**
     * 判断用户是否拥有指定角色。
     */
    public boolean hasRole(RoleName role) {
        return roles.contains(role);
    }

    private static void validateUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new DomainException("用户名不能为空");
        }
        if (username.length() > 64) {
            throw new DomainException("用户名不能超过64个字符");
        }
    }

    public UserId getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Set<RoleName> getRoles() {
        return Set.copyOf(roles);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
