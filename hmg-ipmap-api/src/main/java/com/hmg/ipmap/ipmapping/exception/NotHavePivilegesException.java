package com.hmg.ipmap.ipmapping.exception;

import com.hmg.ipmap.common.exception.GlobalException;
import org.springframework.http.HttpStatus;

public class NotHavePivilegesException extends GlobalException {

    public NotHavePivilegesException() {
        super(HttpStatus.FORBIDDEN, "You do not have privileges");
    }
}
