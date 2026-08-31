package com.hmg.ipmap.location.exception;

import com.hmg.ipmap.common.exception.GlobalException;
import org.springframework.http.HttpStatus;

/**
 * Exception thrown when an unexpected error occurs during a location operation.
 *
 * <p>Maps to HTTP 500 Internal Server Error.
 */
public class LocationInternalServerErrorException extends GlobalException {
    public LocationInternalServerErrorException(String message) {

        super(HttpStatus.INTERNAL_SERVER_ERROR, message);
    }
}
