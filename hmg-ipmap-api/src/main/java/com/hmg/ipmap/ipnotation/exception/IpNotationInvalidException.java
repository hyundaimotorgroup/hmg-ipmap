package com.hmg.ipmap.ipnotation.exception;

public class IpNotationInvalidException extends RuntimeException {

    public IpNotationInvalidException(String message) {
        super("Invalid IP Notation: " + message);
    }
}
