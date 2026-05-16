package com.synapse.auth.repository;

import com.synapse.auth.model.UserAccount;
import com.synapse.auth.model.UserId;

import java.util.List;
import java.util.Optional;

public interface UserAccountRepository {
    UserAccount save(UserAccount user);

    Optional<UserAccount> findById(UserId id);

    Optional<UserAccount> findByUsername(String username);

    List<UserAccount> findAll(int page, int size);

    boolean existsByUsername(String username);
}
