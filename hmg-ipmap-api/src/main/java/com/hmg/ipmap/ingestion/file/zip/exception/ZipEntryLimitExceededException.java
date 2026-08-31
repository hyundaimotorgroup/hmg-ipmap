package com.hmg.ipmap.ingestion.file.zip.exception;

import com.hmg.ipmap.common.exception.GlobalException;
import org.springframework.http.HttpStatus;

public class ZipEntryLimitExceededException extends GlobalException {
    public ZipEntryLimitExceededException(String message) {
        super(HttpStatus.CONTENT_TOO_LARGE, message);
    }
}
