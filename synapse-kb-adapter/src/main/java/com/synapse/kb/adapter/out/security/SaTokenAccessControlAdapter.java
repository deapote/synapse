package com.synapse.kb.adapter.out.security;

import cn.dev33.satoken.stp.StpUtil;
import com.synapse.kb.port.out.AccessControlPort;
import org.springframework.stereotype.Component;

@Component
public class SaTokenAccessControlAdapter implements AccessControlPort {
    @Override
    public String currentUserId() {
        StpUtil.checkLogin();
        return StpUtil.getLoginIdAsString();
    }

    @Override
    public void checkPermission(String permission) {
        StpUtil.checkPermission(permission);
    }

    @Override
    public boolean isAdmin() {
        return StpUtil.hasRole("ADMIN");
    }
}
