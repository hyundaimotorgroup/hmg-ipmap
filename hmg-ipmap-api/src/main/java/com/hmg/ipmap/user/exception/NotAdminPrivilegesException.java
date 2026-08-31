package com.hmg.ipmap.user.exception;

import com.hmg.ipmap.common.exception.GlobalException;
import org.springframework.http.HttpStatus;

public class NotAdminPrivilegesException extends GlobalException {

    public NotAdminPrivilegesException() {
        super(HttpStatus.FORBIDDEN, "You do not have administrator privileges");
    }
}
