package com.hmg.ipmap.common.exception;

import org.springframework.http.HttpStatus;

public class NotFoundException extends GlobalException {

    public NotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, message);
    }
}
