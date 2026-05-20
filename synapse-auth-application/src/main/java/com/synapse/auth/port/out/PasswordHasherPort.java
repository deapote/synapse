package com.synapse.auth.port.out;

/**
 * 密码哈希出站端口。抽象密码哈希算法，使应用层不依赖 BCrypt。
 * 实现类负责安全哈希生成与比对，不暴露明文密码。
 */
public interface PasswordHasherPort {
    String hash(String rawPassword);

    boolean matches(String rawPassword, String passwordHash);
}
