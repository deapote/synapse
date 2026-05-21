package com.synapse.kb.port.service.support;

/**
 * 失败原因安全化辅助类。
 * 截断异常信息并脱敏，防止内部堆栈泄露到文档状态或日志。
 */
public class FailureReasonSanitizer {
    private static final int MAX_FAILURE_REASON_LENGTH = 500;

    public String safeFailureReason(Exception e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) {
            message = e.getClass().getSimpleName();
        }
        return message.length() <= MAX_FAILURE_REASON_LENGTH
                ? message
                : message.substring(0, MAX_FAILURE_REASON_LENGTH);
    }
}
