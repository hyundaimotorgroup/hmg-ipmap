package com.hmg.ipmap.user.exception;

import com.hmg.ipmap.common.exception.GlobalException;
import org.springframework.http.HttpStatus;

public class AccessDeniedException extends GlobalException {

    public AccessDeniedException() {
        super(HttpStatus.FORBIDDEN, "Access Denied");
    }
}
