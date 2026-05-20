package com.synapse.auth.config;

import com.synapse.auth.port.out.LoginSessionPort;
import com.synapse.auth.port.out.PasswordHasherPort;
import com.synapse.auth.port.service.AuthApplicationService;
import com.synapse.auth.repository.RoleDefinitionRepository;
import com.synapse.auth.repository.UserAccountRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Auth Bean 组装配置。将 domain/application/adapter 层对象装配为 Spring Bean，
 * 不写业务流程。具体持久化和安全适配器由各自模块的 {@code @Component} 自动扫描。
 */
@Configuration
public class AuthBeanConfig {
    @Bean
    public AuthApplicationService authApplicationService(
            UserAccountRepository userRepository,
            RoleDefinitionRepository roleRepository,
            PasswordHasherPort passwordHasher,
            LoginSessionPort loginSession) {
        return new AuthApplicationService(userRepository, roleRepository, passwordHasher, loginSession);
    }
}
