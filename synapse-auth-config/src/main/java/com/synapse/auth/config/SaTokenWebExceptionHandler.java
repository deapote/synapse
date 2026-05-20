package com.synapse.auth.config;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.SaTokenException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * WebFlux 全局异常处理器。将 Sa-Token 的未登录、无权限异常转换为结构化 JSON 响应。
 * 优先级最高，确保在 Spring 默认异常处理之前拦截认证相关异常。
 * 不暴露内部堆栈给前端。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SaTokenWebExceptionHandler implements WebExceptionHandler {

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable error) {
        Throwable cause = unwrap(error);
        if (cause instanceof NotLoginException) {
            return writeJson(exchange, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "用户未登录或登录已失效");
        }
        if (cause instanceof NotPermissionException) {
            return writeJson(exchange, HttpStatus.FORBIDDEN, "FORBIDDEN", "权限不足");
        }
        return Mono.error(error);
    }

    private Throwable unwrap(Throwable error) {
        if (error instanceof SaTokenException && error.getCause() != null) {
            return error.getCause();
        }
        return error;
    }

    private Mono<Void> writeJson(ServerWebExchange exchange, HttpStatus status, String error, String message) {
        byte[] bytes = """
                {"error":"%s","message":"%s","timestamp":"%s"}\
                """.formatted(error, message, Instant.now()).getBytes(StandardCharsets.UTF_8);
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
}
