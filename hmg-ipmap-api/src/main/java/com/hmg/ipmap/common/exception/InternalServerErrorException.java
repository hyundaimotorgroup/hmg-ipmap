package com.hmg.ipmap.common.exception;

import org.springframework.http.HttpStatus;

public class InternalServerErrorException extends GlobalException {

    public InternalServerErrorException(String message) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, message);
    }

    public InternalServerErrorException(String message, Throwable cause) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, message, cause);
    }
}
