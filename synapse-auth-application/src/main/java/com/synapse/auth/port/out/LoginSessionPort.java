package com.synapse.auth.port.out;

import com.synapse.auth.model.UserId;

import java.util.Optional;

public interface LoginSessionPort {
    void login(UserId userId);

    void logout();

    Optional<UserId> currentUserId();
}
