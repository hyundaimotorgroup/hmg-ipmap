package com.hmg.ipmap.cache.exception;

import com.hmg.ipmap.common.exception.GlobalException;
import org.springframework.http.HttpStatus;

public class CacheOperationException extends GlobalException {
    public CacheOperationException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
