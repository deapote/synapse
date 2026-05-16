package com.synapse.auth.adapter.config;

import cn.dev33.satoken.reactor.filter.SaReactorFilter;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
