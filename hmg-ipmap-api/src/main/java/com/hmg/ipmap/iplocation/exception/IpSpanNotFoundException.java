package com.hmg.ipmap.iplocation.exception;

import com.hmg.ipmap.common.exception.GlobalException;
import org.springframework.http.HttpStatus;

public class IpSpanNotFoundException extends GlobalException {
    public IpSpanNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, message);
    }
}
