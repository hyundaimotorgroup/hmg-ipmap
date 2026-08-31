package com.hmg.ipmap.user.exception;

import com.hmg.ipmap.common.exception.GlobalException;
import org.springframework.http.HttpStatus;

public class UserNotFoundException extends GlobalException {
    public UserNotFoundException() {

        super(HttpStatus.NOT_FOUND, "User with given API key not found");
    }

    public UserNotFoundException(String message) {

        super(HttpStatus.NOT_FOUND, message);
    }
}
