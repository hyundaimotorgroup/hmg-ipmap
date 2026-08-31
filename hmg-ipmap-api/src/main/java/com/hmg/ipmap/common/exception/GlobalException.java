package com.hmg.ipmap.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class GlobalException extends RuntimeException {
    private final HttpStatus status;

    public GlobalException(HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public GlobalException(HttpStatus status, String message) {
        this(status, message, null);
    }
}
