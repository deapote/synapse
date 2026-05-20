package com.synapse.auth.port.out;

import com.synapse.auth.model.UserId;

import java.util.Optional;

/**
 * 登录会话出站端口。抽象登录态管理，使应用层不依赖 Sa-Token。
 * 具体实现由 {@code synapse-auth-adapter} 中的 Sa-Token adapter 提供。
 */
public interface LoginSessionPort {
    void login(UserId userId);

    void logout();

    Optional<UserId> currentUserId();
}
