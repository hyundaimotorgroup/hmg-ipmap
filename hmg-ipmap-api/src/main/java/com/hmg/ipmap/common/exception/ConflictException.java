package com.hmg.ipmap.common.exception;

import org.springframework.http.HttpStatus;

public class ConflictException extends GlobalException {

    public ConflictException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
