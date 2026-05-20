package com.synapse.auth.adapter.in.web;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.time.Instant;
import java.util.Map;

/**
 * 全局认证异常处理器。将 Sa-Token 的未登录、无权限异常转换为结构化 HTTP 响应，
 * 不暴露内部堆栈给前端。
 */
@ControllerAdvice
public class SaTokenExceptionHandler {
    @ExceptionHandler(NotLoginException.class)
    public ResponseEntity<Map<String, Object>> handleNotLogin(NotLoginException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                "error", "UNAUTHORIZED",
                "message", "用户未登录",
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(NotPermissionException.class)
    public ResponseEntity<Map<String, Object>> handleNotPermission(NotPermissionException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                "error", "FORBIDDEN",
                "message", "权限不足",
                "timestamp", Instant.now().toString()
        ));
    }
}
