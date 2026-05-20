package com.synapse.auth.adapter.out.security;

import com.synapse.auth.port.out.PasswordHasherPort;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * BCrypt 密码哈希适配器。强度因子固定为 12，负责安全哈希生成与比对。
 * 只接收和返回哈希值，不存储、不传输明文密码。
 */
@Component
public class BCryptPasswordHasherAdapter implements PasswordHasherPort {
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);

    @Override
    public String hash(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    @Override
    public boolean matches(String rawPassword, String passwordHash) {
        return encoder.matches(rawPassword, passwordHash);
    }
}
