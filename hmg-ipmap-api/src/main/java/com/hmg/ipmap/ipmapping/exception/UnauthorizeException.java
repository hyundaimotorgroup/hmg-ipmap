package com.hmg.ipmap.ipmapping.exception;

import com.hmg.ipmap.common.exception.GlobalException;
import org.springframework.http.HttpStatus;

public class UnauthorizeException extends GlobalException {

    public UnauthorizeException(String message) {
        super(HttpStatus.FORBIDDEN, message);
    }
}
