package com.synapse.kb.exception;

import com.synapse.shared.exception.DomainException;

/** 文档摄入阶段异常。 */
public class IngestionException extends DomainException {

    public IngestionException(String message) {
        super(message);
    }

    public IngestionException(String message, Throwable cause) {
        super(message, cause);
    }
}
