package com.hmg.ipmap.location.exception;

import com.hmg.ipmap.common.exception.GlobalException;
import org.springframework.http.HttpStatus;

/**
 * Exception thrown when a location request contains invalid or malformed input.
 *
 * <p>Maps to HTTP 400 Bad Request.
 */
public class LocationBadRequestException extends GlobalException {
    public LocationBadRequestException(String message) {

        super(HttpStatus.BAD_REQUEST, message);
    }
}
