package com.hmg.ipmap.ipnotation.exception;

import com.hmg.ipmap.common.exception.GlobalException;
import org.springframework.http.HttpStatus;

public class IpNotationBadRequestException extends GlobalException {
    public IpNotationBadRequestException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
