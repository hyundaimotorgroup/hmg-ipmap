package com.hmg.ipmap.cache.exception;

import com.hmg.ipmap.common.exception.GlobalException;
import org.springframework.http.HttpStatus;

public class CacheSyncTimeoutException extends GlobalException {

    public CacheSyncTimeoutException(String message, Throwable cause) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, message, cause);
    }
}
