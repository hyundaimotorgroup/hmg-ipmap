package com.hmg.ipmap.user.exception;

import com.hmg.ipmap.common.exception.GlobalException;
import org.springframework.http.HttpStatus;

public class UserAlreadyExistException extends GlobalException {
    public UserAlreadyExistException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
