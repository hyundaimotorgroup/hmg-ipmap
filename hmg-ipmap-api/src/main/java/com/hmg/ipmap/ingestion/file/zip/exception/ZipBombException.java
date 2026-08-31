package com.hmg.ipmap.ingestion.file.zip.exception;

import com.hmg.ipmap.common.exception.GlobalException;
import org.springframework.http.HttpStatus;

public class ZipBombException extends GlobalException {
    public ZipBombException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
