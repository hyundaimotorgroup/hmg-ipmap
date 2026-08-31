package com.hmg.ipmap.user.exception;

import com.hmg.ipmap.common.exception.GlobalException;
import org.springframework.http.HttpStatus;

public class ApiKeyUnauthorizeException extends GlobalException {

    public ApiKeyUnauthorizeException(String message) {
        super(HttpStatus.UNAUTHORIZED, message);
    }
}
