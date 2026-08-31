package com.hmg.ipmap.ingestion.file.zip.exception;

import com.hmg.ipmap.common.exception.GlobalException;
import org.springframework.http.HttpStatus;

public class ZipSlipException extends GlobalException {
    public ZipSlipException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
