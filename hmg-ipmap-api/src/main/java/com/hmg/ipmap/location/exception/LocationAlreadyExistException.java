package com.hmg.ipmap.location.exception;

import com.hmg.ipmap.common.exception.GlobalException;
import org.springframework.http.HttpStatus;

/**
 * Exception thrown when a location creation or update would conflict with an existing record.
 *
 * <p>Maps to HTTP 409 Conflict.
 */
public class LocationAlreadyExistException extends GlobalException {
    public LocationAlreadyExistException(String message) {

        super(HttpStatus.CONFLICT, message);
    }
}
