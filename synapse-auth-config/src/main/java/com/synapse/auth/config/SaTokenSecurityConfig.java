package com.synapse.auth.config;

import cn.dev33.satoken.reactor.filter.SaReactorFilter;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Sa-Token 安全过滤器配置。职责：
 * - 保护所有 {@code /api/**} 接口
 * - 放行 {@code /api/auth/login} 登录接口
 * 其他 CORS 和认证规则由上层配置控制。
 */
@Configuration
public class SaTokenSecurityConfig {
    @Bean
    public SaReactorFilter saReactorFilter() {
        return new SaReactorFilter()
                .addInclude("/api/**")
                .addExclude("/api/auth/login")
                .setAuth(obj -> SaRouter.match("/api/**", "/api/auth/login", StpUtil::checkLogin));
    }
}
