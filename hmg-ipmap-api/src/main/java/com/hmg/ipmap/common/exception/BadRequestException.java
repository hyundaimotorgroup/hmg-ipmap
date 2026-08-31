package com.hmg.ipmap.common.exception;

import org.springframework.http.HttpStatus;

public class BadRequestException extends GlobalException {

    public BadRequestException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
