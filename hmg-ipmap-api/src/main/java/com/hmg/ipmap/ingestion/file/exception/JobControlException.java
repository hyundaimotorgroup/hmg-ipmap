package com.hmg.ipmap.ingestion.file.exception;

import com.hmg.ipmap.common.exception.GlobalException;
import org.springframework.http.HttpStatus;

public class JobControlException extends GlobalException {

    public JobControlException(HttpStatus status, String message) {
        super(status, message);
    }
}
