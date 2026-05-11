package com.synapse.kb.adapter.in.web;

import com.synapse.shared.exception.DomainException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.time.Instant;
import java.util.Map;

/**
 * 全局异常处理器。
 *
 * <p>统一捕获 Controller 层抛出的异常，转换为结构化的 HTTP 错误响应。
 * 避免框架默认的 HTML 错误页，确保前后端对接时始终拿到 JSON。
 *
 * <p>异常分类处理：
 * <ul>
 *   <li>{@link DomainException} → 400 Bad Request（业务规则违反）</li>
 *   <li>其他运行时异常 → 500 Internal Server Error（系统故障）</li>
 * </ul>
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 处理领域异常（业务规则违反）。
     *
     * <p>如：状态机非法流转、文档重复上传、参数校验失败等。
     *
     * @param e 领域异常
     * @return 400 Bad Request + 结构化错误体
     */
    @ExceptionHandler(DomainException.class)
    public ResponseEntity<Map<String, Object>> handleDomainException(DomainException e) {
        log.warn("业务异常: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of(
                        "error", "BUSINESS_ERROR",
                        "message", e.getMessage(),
                        "timestamp", Instant.now().toString()
                ));
    }

    /**
     * 处理未预料的系统异常。
     *
     * <p>如：数据库连接中断、Ollama 服务不可用、NPE 等。
     * 在生产环境中不应把内部异常详情暴露给客户端。
     *
     * @param e 运行时异常
     * @return 500 Internal Server Error + 结构化错误体
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(Exception e) {
        log.error("系统异常", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                        "error", "INTERNAL_ERROR",
                        "message", "系统内部错误，请联系管理员",
                        "timestamp", Instant.now().toString()
                ));
    }
}
