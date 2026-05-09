package com.sun.synapse.shared.exception;

/**
 * 领域异常，所有业务异常的根类
 */
public class DomainException extends RuntimeException {
    public DomainException(String message){
        super(message);
    }

    /**
     *
     * @param message 给用户看的业务错误描述
     * @param cause   底层技术异常(保留用于调试)
     */
    public DomainException(String message, Throwable cause){
        super(message, cause);
    }
}
