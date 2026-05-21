package com.synapse.kb.adapter.out.security;

import cn.dev33.satoken.stp.StpUtil;
import com.synapse.kb.port.out.AccessControlPort;
import org.springframework.stereotype.Component;

/**
 * 基于 Sa-Token 的访问控制适配器，实现 AccessControlPort。
 * 桥接 Sa-Token 登录态与权限校验能力到知识库应用层。
 */
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
