package com.synapse.auth.adapter.out.security;

import cn.dev33.satoken.stp.StpUtil;
import com.synapse.auth.model.UserId;
import com.synapse.auth.port.out.LoginSessionPort;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class SaTokenLoginSessionAdapter implements LoginSessionPort {
    @Override
    public void login(UserId userId) {
        StpUtil.login(userId.value());
    }

    @Override
    public void logout() {
        StpUtil.logout();
    }

    @Override
    public Optional<UserId> currentUserId() {
        if (!StpUtil.isLogin()) {
            return Optional.empty();
        }
        return Optional.of(new UserId(StpUtil.getLoginIdAsString()));
    }
}
