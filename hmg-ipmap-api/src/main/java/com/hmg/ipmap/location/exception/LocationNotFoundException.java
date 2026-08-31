package com.hmg.ipmap.location.exception;

import com.hmg.ipmap.common.exception.GlobalException;
import org.springframework.http.HttpStatus;

/**
 * Exception thrown when a requested location cannot be found.
 *
 * <p>Maps to HTTP 404 Not Found.
 */
public class LocationNotFoundException extends GlobalException {
    public LocationNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, message);
    }

    public LocationNotFoundException() {
        super(HttpStatus.NOT_FOUND, "Location not found");
    }
}
