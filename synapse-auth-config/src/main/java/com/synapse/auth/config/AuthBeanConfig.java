package com.synapse.auth.config;

import com.synapse.auth.port.out.LoginSessionPort;
import com.synapse.auth.port.out.PasswordHasherPort;
import com.synapse.auth.port.service.AuthApplicationService;
import com.synapse.auth.repository.RoleDefinitionRepository;
import com.synapse.auth.repository.UserAccountRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
