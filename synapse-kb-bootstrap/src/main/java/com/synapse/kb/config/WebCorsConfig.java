package com.synapse.kb.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

/**
 * 全局 CORS 配置。
 *
 * <p>前端开发服务器（Vite 默认 5173）与后端（8082）端口不同，
 * 生产环境前后端分离部署时也需要跨域支持。
 *
 * <p>配置说明：
 * <ul>
 *   <li>允许所有来源（{@code *}）—— 开发环境便利，生产环境建议收紧为具体域名</li>
 *   <li>不允许携带凭证（cookies）—— 与 {@code *} 来源兼容，避免安全漏洞</li>
 *   <li>允许所有常见 HTTP 方法</li>
 *   <li>允许 {@code Content-Type} 和 {@code Authorization} 请求头</li>
 * </ul>
 *
 * <p>生产环境应收紧为具体域名并视需要启用凭证：
 * <pre>
 *   config.addAllowedOrigin("https://your-domain.com");
 *   config.setAllowCredentials(true);
 * </pre>
 */
@Configuration
public class WebCorsConfig {
    private final String allowedOrigins;

    public WebCorsConfig(@Value("${synapse.cors.allowed-origins:http://localhost:5173,http://127.0.0.1:5173}") String allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration config = new CorsConfiguration();
        for (String origin : allowedOrigins.split(",")) {
            if (!origin.isBlank()) {
                config.addAllowedOriginPattern(origin.trim());
            }
        }
        config.addAllowedMethod("*");
        config.addAllowedHeader("*");
        config.setAllowCredentials(false);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return new CorsWebFilter(source);
    }
}
