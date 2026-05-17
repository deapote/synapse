package com.synapse.auth.config;

import com.synapse.auth.model.AuthPermission;
import com.synapse.auth.model.RoleDefinition;
import com.synapse.auth.model.RoleName;
import com.synapse.auth.model.UserAccount;
import com.synapse.auth.port.out.PasswordHasherPort;
import com.synapse.auth.repository.RoleDefinitionRepository;
import com.synapse.auth.repository.UserAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessException;

import java.util.EnumSet;

@Configuration
public class AuthDataInitializer {
    private static final Logger log = LoggerFactory.getLogger(AuthDataInitializer.class);

    @Bean
    ApplicationRunner seedAuthData(RoleDefinitionRepository roleRepository,
                                   UserAccountRepository userRepository,
                                   PasswordHasherPort passwordHasher,
                                   @Value("${synapse.auth.bootstrap-admin.username:admin}") String adminUsername,
                                   @Value("${synapse.auth.bootstrap-admin.password:ChangeMe123!}") String adminPassword) {
        return args -> {
            try {
                if ("ChangeMe123!".equals(adminPassword)) {
                    log.warn("当前使用默认 bootstrap admin 密码，请在生产环境通过 synapse.auth.bootstrap-admin.password 覆盖");
                }
                if (roleRepository.findByName(RoleName.ADMIN).isEmpty()) {
                    roleRepository.save(RoleDefinition.create(RoleName.ADMIN, EnumSet.allOf(AuthPermission.class)));
                }
                if (roleRepository.findByName(RoleName.USER).isEmpty()) {
                    roleRepository.save(RoleDefinition.create(RoleName.USER,
                            EnumSet.of(AuthPermission.KB_READ, AuthPermission.KB_WRITE)));
                }
                if (!userRepository.existsByUsername(adminUsername)) {
                    UserAccount admin = UserAccount.create(adminUsername, "Administrator",
                            passwordHasher.hash(adminPassword), EnumSet.of(RoleName.ADMIN));
                    userRepository.save(admin);
                }
            } catch (DataAccessException e) {
                log.warn("MongoDB 不可用，跳过认证种子数据初始化: {}", e.getMessage());
            }
        };
    }
}
