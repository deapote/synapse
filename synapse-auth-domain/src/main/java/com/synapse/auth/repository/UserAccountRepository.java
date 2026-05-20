package com.synapse.auth.repository;

import com.synapse.auth.model.UserAccount;
import com.synapse.auth.model.UserId;

import java.util.List;
import java.util.Optional;

/** 用户账号仓储接口。领域层只定义契约，持久化由 adapter 实现。 */
public interface UserAccountRepository {
    UserAccount save(UserAccount user);

    Optional<UserAccount> findById(UserId id);

    Optional<UserAccount> findByUsername(String username);

    List<UserAccount> findAll(int page, int size);

    boolean existsByUsername(String username);
}
